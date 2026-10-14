package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.ShoppingGroupDto;
import io.everyonecodes.deliciousnessness.dto.ShoppingItemDto;
import io.everyonecodes.deliciousnessness.dto.ShoppingListDto;
import io.everyonecodes.deliciousnessness.model.PlannerEntry;
import io.everyonecodes.deliciousnessness.model.PlannerWeek;
import io.everyonecodes.deliciousnessness.model.RecipeIngredient;
import io.everyonecodes.deliciousnessness.model.ShoppingItem;
import io.everyonecodes.deliciousnessness.repository.PlannerEntryRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerWeekRepository;
import io.everyonecodes.deliciousnessness.repository.ShoppingItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ShoppingListService {

    private static final int NAME_MAX = 128;
    private static final int AMOUNT_MAX = 64;

    // "400 g" -> 400 and "g". "1,5 tbsp" -> 1.5 and "tbsp".
    private static final Pattern AMOUNT = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*(.*)$");

    private final ShoppingItemRepository shoppingItemRepository;
    private final PlannerWeekRepository plannerWeekRepository;
    private final PlannerEntryRepository plannerEntryRepository;

    public ShoppingListService(ShoppingItemRepository shoppingItemRepository,
                               PlannerWeekRepository plannerWeekRepository,
                               PlannerEntryRepository plannerEntryRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.plannerWeekRepository = plannerWeekRepository;
        this.plannerEntryRepository = plannerEntryRepository;
    }

    // Nothing is built automatically, so a list you cleared stays cleared.
    @Transactional(readOnly = true)
    public ShoppingListDto open(Long weekId) {
        return toDto(findUpcomingWeek(weekId));
    }

    // Builds the list from the planned recipes, or updates the lines that came from recipes.
    // Lines added by hand are kept.
    @Transactional
    public void rebuild(Long weekId) {
        rebuildFromRecipes(findUpcomingWeek(weekId));
    }

    // Removes every line, including the ones added by hand.
    @Transactional
    public void clear(Long weekId) {
        findUpcomingWeek(weekId);
        shoppingItemRepository.deleteByWeekId(weekId);
    }

    @Transactional
    public void addItem(Long weekId, String name, String amount) {
        PlannerWeek week = findUpcomingWeek(weekId);
        String cleanName = clean(name, NAME_MAX);
        if (cleanName == null) return;
        shoppingItemRepository.save(new ShoppingItem(week, cleanName, clean(amount, AMOUNT_MAX), false));
    }

    @Transactional
    public void updateItem(Long weekId, Long itemId, String name, String amount) {
        findUpcomingWeek(weekId);
        String cleanName = clean(name, NAME_MAX);
        shoppingItemRepository.findByIdAndWeekId(itemId, weekId).ifPresent(item -> {
            if (cleanName != null) item.setName(cleanName);
            item.setAmount(clean(amount, AMOUNT_MAX));
        });
    }

    @Transactional
    public void removeItem(Long weekId, Long itemId) {
        findUpcomingWeek(weekId);
        shoppingItemRepository.findByIdAndWeekId(itemId, weekId).ifPresent(shoppingItemRepository::delete);
    }

    private void rebuildFromRecipes(PlannerWeek week) {
        shoppingItemRepository.deleteByWeekIdAndFromRecipesTrue(week.getId());

        // Key = ingredient + unit. Every planned entry counts once, so the same recipe on
        // two days is counted twice.
        Map<String, Total> totals = new LinkedHashMap<>();
        for (PlannerEntry card : plannerEntryRepository.findAllByWeekIdOrderByIdAsc(week.getId())) {
            // A card is one serving, so it adds 1/servings of the recipe's ingredients.
            double share = 1.0 / PlannerService.servingsOf(card.getRecipe());
            for (RecipeIngredient line : card.getRecipe().getRecipeIngredients()) {
                String unit = line.getUnit() == null ? "" : line.getUnit().trim();
                String key = line.getIngredient().getId() + "|" + unit.toLowerCase();
                Double quantity = line.getQuantity() == null ? null : line.getQuantity() * share;
                totals.computeIfAbsent(key, k -> new Total(nameOf(line), unit)).add(quantity);
            }
        }
        for (Total total : totals.values()) {
            shoppingItemRepository.save(new ShoppingItem(week, total.name, total.amount(), true));
        }
    }

    private static String nameOf(RecipeIngredient line) {
        return line.getDisplayName() != null && !line.getDisplayName().isBlank()
                ? line.getDisplayName()
                : line.getIngredient().getCanonicalName();
    }

    // Running total for one line made from recipes.
    private static class Total {
        private final String name;
        private final String unit;
        private double quantity;
        private boolean hasQuantity;

        Total(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        void add(Double value) {
            if (value == null) return;
            quantity += value;
            hasQuantity = true;
        }

        // "600 g", "1.5 tbsp", or just "3" with no unit. Null when no amount was ever given ("salt").
        String amount() {
            if (!hasQuantity) return null;
            String number = BigDecimal.valueOf(quantity)
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
            return unit.isEmpty() ? number : number + " " + unit;
        }
    }

    // Turns the stored lines into the combined list, sorted by name.
    private ShoppingListDto toDto(PlannerWeek week) {
        Map<String, List<ShoppingItem>> sameLine = new LinkedHashMap<>();
        for (ShoppingItem item : shoppingItemRepository.findAllByWeekIdOrderByIdAsc(week.getId())) {
            sameLine.computeIfAbsent(sameLineKey(item), key -> new ArrayList<>()).add(item);
        }
        List<ShoppingGroupDto> groups = sameLine.values().stream()
                .map(this::groupOf)
                .sorted(Comparator.comparing(ShoppingGroupDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new ShoppingListDto(week.getId(), week.getStartDate(), groups);
    }

    // Same name and same unit means the same line: "Spaghetti 200 g" and "spaghetti 600 g" combine.
    private static String sameLineKey(ShoppingItem item) {
        return item.getName().trim().toLowerCase() + "|" + parse(item.getAmount()).unitKey();
    }

    // Adds up the numbers of one combined line. Amounts without a number are shown as typed.
    private ShoppingGroupDto groupOf(List<ShoppingItem> sources) {
        BigDecimal total = null;
        String unitText = "";
        for (ShoppingItem item : sources) {
            Parsed parsed = parse(item.getAmount());
            if (parsed.number() == null) continue;
            total = total == null ? parsed.number() : total.add(parsed.number());
            if (unitText.isEmpty()) unitText = parsed.unitText();
        }

        String amount;
        if (total == null) {
            amount = sources.get(0).getAmount();
        } else {
            String number = total.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            amount = unitText.isEmpty() ? number : number + " " + unitText;
        }

        List<ShoppingItemDto> parts = sources.stream()
                .map(item -> new ShoppingItemDto(item.getId(), item.getName(), item.getAmount(), item.isFromRecipes()))
                .toList();
        return new ShoppingGroupDto(sources.get(0).getName(), amount, parts);
    }

    private static Parsed parse(String amount) {
        if (amount == null || amount.isBlank()) return new Parsed(null, "", "");
        String text = amount.trim();
        Matcher matcher = AMOUNT.matcher(text);
        if (matcher.matches()) {
            BigDecimal number = new BigDecimal(matcher.group(1).replace(',', '.'));
            String unit = matcher.group(2).trim();
            return new Parsed(number, unit.toLowerCase(), unit);
        }
        return new Parsed(null, text.toLowerCase(), text);
    }

    private record Parsed(BigDecimal number, String unitKey, String unitText) {
    }

    // Shopping lists are only for upcoming weeks. Past weeks are read-only.
    private PlannerWeek findUpcomingWeek(Long weekId) {
        PlannerWeek week = plannerWeekRepository.findById(weekId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Week not found"));
        if (week.getStartDate().isBefore(PlannerService.currentWeekStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Past weeks are read-only");
        }
        return week;
    }

    // Trims the input, turns blanks into null, and cuts it to the column size.
    private static String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
