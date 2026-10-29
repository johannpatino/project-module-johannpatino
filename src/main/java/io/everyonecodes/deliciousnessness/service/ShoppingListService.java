package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.ShoppingGroupDto;
import io.everyonecodes.deliciousnessness.dto.ShoppingItemDto;
import io.everyonecodes.deliciousnessness.dto.ShoppingListDto;
import io.everyonecodes.deliciousnessness.model.Ingredient;
import io.everyonecodes.deliciousnessness.model.IngredientName;
import io.everyonecodes.deliciousnessness.model.IngredientUnitWeight;
import io.everyonecodes.deliciousnessness.model.PlannerEntry;
import io.everyonecodes.deliciousnessness.model.PlannerWeek;
import io.everyonecodes.deliciousnessness.model.RecipeIngredient;
import io.everyonecodes.deliciousnessness.model.ShoppingItem;
import io.everyonecodes.deliciousnessness.repository.IngredientNameRepository;
import io.everyonecodes.deliciousnessness.repository.IngredientRepository;
import io.everyonecodes.deliciousnessness.repository.IngredientUnitWeightRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerEntryRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerWeekRepository;
import io.everyonecodes.deliciousnessness.repository.ShoppingItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The shopping list of one week. Lines of the same ingredient are one line, named by its English name,
// whatever the recipes called it and whatever unit they wrote it in: "2 ząbki", "1 clove" and "2 zehen" of
// garlic make "4 clove". Amounts are added up when the units match, or in grams when every unit has a
// gram value (Edit ingredients). Otherwise the units are listed with a "+" between them.
@Service
public class ShoppingListService {

    private static final int NAME_MAX = 128;
    private static final int AMOUNT_MAX = 64;

    // "400 g" -> 400 and "g". "1,5 tbsp" -> 1.5 and "tbsp".
    private static final Pattern AMOUNT = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*(.*)$");

    private final ShoppingItemRepository shoppingItemRepository;
    private final PlannerWeekRepository plannerWeekRepository;
    private final PlannerEntryRepository plannerEntryRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientNameRepository ingredientNameRepository;
    private final IngredientUnitWeightRepository weightRepository;

    public ShoppingListService(ShoppingItemRepository shoppingItemRepository,
                               PlannerWeekRepository plannerWeekRepository,
                               PlannerEntryRepository plannerEntryRepository,
                               IngredientRepository ingredientRepository,
                               IngredientNameRepository ingredientNameRepository,
                               IngredientUnitWeightRepository weightRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.plannerWeekRepository = plannerWeekRepository;
        this.plannerEntryRepository = plannerEntryRepository;
        this.ingredientRepository = ingredientRepository;
        this.ingredientNameRepository = ingredientNameRepository;
        this.weightRepository = weightRepository;
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

    // Lines saved before the app stored their ingredient and amount as numbers. Each is linked to its ingredient
    // by its name, in any language, and its amount is read into a number and unit. Then it combines with the
    // other lines of that ingredient.
    @Transactional
    public void linkSavedLines() {
        for (ShoppingItem item : shoppingItemRepository.findAllByIngredientIdIsNull()) {
            describe(item, item.getName(), item.getAmount());
        }
    }

    @Transactional
    public void addItem(Long weekId, String name, String amount) {
        PlannerWeek week = findUpcomingWeek(weekId);
        String cleanName = clean(name, NAME_MAX);
        if (cleanName == null) return;
        ShoppingItem item = new ShoppingItem(week, cleanName, clean(amount, AMOUNT_MAX), false);
        describe(item, cleanName, item.getAmount());
        shoppingItemRepository.save(item);
    }

    @Transactional
    public void updateItem(Long weekId, Long itemId, String name, String amount) {
        findUpcomingWeek(weekId);
        String cleanName = clean(name, NAME_MAX);
        shoppingItemRepository.findByIdAndWeekId(itemId, weekId).ifPresent(item -> {
            String newAmount = clean(amount, AMOUNT_MAX);
            describe(item, cleanName != null ? cleanName : item.getName(), newAmount);
            item.setAmount(newAmount);
        });
    }

    @Transactional
    public void removeItem(Long weekId, Long itemId) {
        findUpcomingWeek(weekId);
        shoppingItemRepository.findByIdAndWeekId(itemId, weekId).ifPresent(shoppingItemRepository::delete);
    }

    // Works out what a typed line is about: its ingredient when the name is one the app knows (then the line
    // takes the English name), and its amount as a number and a unit.
    private void describe(ShoppingItem item, String typedName, String typedAmount) {
        Ingredient ingredient = ingredientNamed(typedName);
        item.setName(ingredient != null ? ingredient.getCanonicalName() : typedName);
        item.setIngredientId(ingredient != null ? ingredient.getId() : null);
        Amount amount = parse(typedAmount);
        item.setQuantity(amount == null ? null : amount.quantity());
        item.setUnit(amount == null ? null : amount.unit());
    }

    // The ingredient a name means: by one of its names in any language, else by its English name.
    private Ingredient ingredientNamed(String name) {
        List<IngredientName> names = ingredientNameRepository.findByNameIgnoreCase(name);
        if (!names.isEmpty()) return names.get(0).getIngredient();
        return ingredientRepository.findByCanonicalNameIgnoreCase(name).orElse(null);
    }

    private void rebuildFromRecipes(PlannerWeek week) {
        shoppingItemRepository.deleteByWeekIdAndFromRecipesTrue(week.getId());

        // One total per ingredient and unit. Each card is one serving, so it adds 1/servings of its recipe.
        Map<String, Total> totals = new LinkedHashMap<>();
        for (PlannerEntry card : plannerEntryRepository.findAllByWeekIdOrderByIdAsc(week.getId())) {
            double share = 1.0 / PlannerService.servingsOf(card.getRecipe());
            for (RecipeIngredient line : card.getRecipe().getRecipeIngredients()) {
                Ingredient ingredient = line.getIngredient();
                String unit = Units.normalize(line.getUnit());
                Double quantity = line.getQuantity() == null ? null : line.getQuantity() * share;
                totals.computeIfAbsent(ingredient.getId() + "|" + unit, key -> new Total(ingredient, unit)).add(quantity);
            }
        }
        for (Total total : totals.values()) {
            ShoppingItem item = new ShoppingItem(week, total.ingredient.getCanonicalName(),
                    total.quantity == null ? null : numberWithUnit(total.quantity, total.unit), true);
            item.setIngredientId(total.ingredient.getId());
            item.setQuantity(total.quantity);
            item.setUnit(total.unit);
            shoppingItemRepository.save(item);
        }
    }

    // The running total of one ingredient in one unit, from the recipes. Null until a line has an amount.
    private static class Total {
        private final Ingredient ingredient;
        private final String unit;
        private Double quantity;

        Total(Ingredient ingredient, String unit) {
            this.ingredient = ingredient;
            this.unit = unit;
        }

        void add(Double value) {
            if (value == null) return;
            quantity = (quantity == null ? 0.0 : quantity) + value;
        }
    }

    // Turns the stored lines into the combined list, sorted by name.
    private ShoppingListDto toDto(PlannerWeek week) {
        List<ShoppingItem> items = shoppingItemRepository.findAllByWeekIdOrderByIdAsc(week.getId());

        Map<String, List<ShoppingItem>> lines = new LinkedHashMap<>();
        for (ShoppingItem item : items) {
            lines.computeIfAbsent(groupKey(item), key -> new ArrayList<>()).add(item);
        }
        Map<Long, Map<String, Double>> gramsByIngredient = gramsFor(items);
        List<ShoppingGroupDto> groups = lines.values().stream()
                .map(sources -> groupOf(sources, gramsByIngredient))
                .sorted(Comparator.comparing(ShoppingGroupDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new ShoppingListDto(week.getId(), week.getStartDate(), groups);
    }

    // Lines of one ingredient are one line. A line with no ingredient is grouped by its name.
    private static String groupKey(ShoppingItem item) {
        return item.getIngredientId() != null
                ? "ingredient " + item.getIngredientId()
                : "name " + item.getName().trim().toLowerCase(Locale.ROOT);
    }

    // Grams for one unit of each ingredient on the list, from the Edit ingredients page.
    private Map<Long, Map<String, Double>> gramsFor(List<ShoppingItem> items) {
        Set<Long> ids = new HashSet<>();
        for (ShoppingItem item : items) {
            if (item.getIngredientId() != null) ids.add(item.getIngredientId());
        }
        Map<Long, Map<String, Double>> grams = new HashMap<>();
        if (ids.isEmpty()) return grams;
        for (IngredientUnitWeight weight : weightRepository.findAllByIngredientIdIn(ids)) {
            grams.computeIfAbsent(weight.getIngredient().getId(), key -> new HashMap<>())
                    .put(Units.normalize(weight.getUnit()), weight.getGrams());
        }
        return grams;
    }

    // One line: the parts added up, then any text amounts with a "+", and the stored lines it was made from.
    private ShoppingGroupDto groupOf(List<ShoppingItem> sources, Map<Long, Map<String, Double>> gramsByIngredient) {
        Map<String, Double> perUnit = new LinkedHashMap<>();
        List<String> texts = new ArrayList<>();
        for (ShoppingItem item : sources) {
            Amount amount = amountOf(item);
            if (amount != null) {
                perUnit.merge(amount.unit(), amount.quantity(), Double::sum);
            } else if (item.getAmount() != null && !item.getAmount().isBlank()) {
                texts.add(item.getAmount().trim());
            }
        }

        Long ingredientId = sources.get(0).getIngredientId();
        Map<String, Double> grams = ingredientId == null
                ? Map.of()
                : gramsByIngredient.getOrDefault(ingredientId, Map.of());

        List<String> pieces = new ArrayList<>();
        if (perUnit.size() == 1) {
            Map.Entry<String, Double> only = perUnit.entrySet().iterator().next();
            pieces.add(numberWithUnit(only.getValue(), only.getKey()));
        } else if (perUnit.size() > 1 && perUnit.keySet().stream().allMatch(unit -> gramsOf(unit, grams) != null)) {
            double total = perUnit.entrySet().stream()
                    .mapToDouble(part -> part.getValue() * gramsOf(part.getKey(), grams))
                    .sum();
            pieces.add(numberWithUnit(total, "g"));
        } else {
            perUnit.forEach((unit, quantity) -> pieces.add(numberWithUnit(quantity, unit)));
        }
        pieces.addAll(texts);

        List<ShoppingItemDto> parts = sources.stream()
                .map(item -> new ShoppingItemDto(item.getId(), item.getName(), item.getAmount(), item.isFromRecipes()))
                .toList();
        return new ShoppingGroupDto(sources.get(0).getName(),
                pieces.isEmpty() ? null : String.join(" + ", pieces), parts);
    }

    // Grams in one unit: the built-in weights (g, kg), or the ingredient's own. Null when there is no value.
    private static Double gramsOf(String unit, Map<String, Double> ingredientGrams) {
        if (Units.isCounted(unit)) return null;
        Double builtIn = Units.weightInGrams(unit);
        return builtIn != null ? builtIn : ingredientGrams.get(unit);
    }

    // The number and unit of a line. A line without a stored number (made before they were stored) is read
    // from its text.
    private static Amount amountOf(ShoppingItem item) {
        if (item.getQuantity() != null) {
            return new Amount(item.getQuantity(), item.getUnit() == null ? "" : item.getUnit());
        }
        return parse(item.getAmount());
    }

    // "400 g" -> 400 g. "1,5 tbsp" -> 1.5 tbsp. Null for text such as "a pinch".
    private static Amount parse(String amount) {
        if (amount == null || amount.isBlank()) return null;
        Matcher matcher = AMOUNT.matcher(amount.trim());
        if (!matcher.matches()) return null;
        double quantity = new BigDecimal(matcher.group(1).replace(',', '.')).doubleValue();
        return new Amount(quantity, Units.normalize(matcher.group(2)));
    }

    private record Amount(double quantity, String unit) {
    }

    private static String numberWithUnit(double quantity, String unit) {
        String number = BigDecimal.valueOf(quantity).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return unit.isEmpty() ? number : number + " " + unit;
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
