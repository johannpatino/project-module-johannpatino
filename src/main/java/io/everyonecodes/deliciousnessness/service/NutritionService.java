package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.DayNutritionDto;
import io.everyonecodes.deliciousnessness.dto.DishNutritionDto;
import io.everyonecodes.deliciousnessness.dto.NutritionGapDto;
import io.everyonecodes.deliciousnessness.dto.NutritionRowDto;
import io.everyonecodes.deliciousnessness.dto.NutritionTileDto;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.dto.RecipeIngredientDto;
import io.everyonecodes.deliciousnessness.dto.RecipeNutritionDto;
import io.everyonecodes.deliciousnessness.model.Ingredient;
import io.everyonecodes.deliciousnessness.model.IngredientUnitWeight;
import io.everyonecodes.deliciousnessness.model.Nutrient;
import io.everyonecodes.deliciousnessness.model.PlannerEntry;
import io.everyonecodes.deliciousnessness.model.PlannerExtra;
import io.everyonecodes.deliciousnessness.model.PlannerPerson;
import io.everyonecodes.deliciousnessness.repository.IngredientRepository;
import io.everyonecodes.deliciousnessness.repository.IngredientUnitWeightRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerEntryRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerExtraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

// Adds up nutrition from the ingredient lines:
// quantity x grams per unit = grams, then grams / 100 x the ingredient's values per 100 g.
@Service
public class NutritionService {

    private final RecipeService recipeService;
    private final IngredientRepository ingredientRepository;
    private final IngredientUnitWeightRepository weightRepository;
    private final PlannerEntryRepository plannerEntryRepository;
    private final PlannerExtraRepository plannerExtraRepository;

    public NutritionService(RecipeService recipeService, IngredientRepository ingredientRepository,
                            IngredientUnitWeightRepository weightRepository,
                            PlannerEntryRepository plannerEntryRepository,
                            PlannerExtraRepository plannerExtraRepository) {
        this.recipeService = recipeService;
        this.ingredientRepository = ingredientRepository;
        this.weightRepository = weightRepository;
        this.plannerEntryRepository = plannerEntryRepository;
        this.plannerExtraRepository = plannerExtraRepository;
    }

    // One recipe, as on its own page: each number is per serving, with the whole dish under it.
    @Transactional(readOnly = true)
    public RecipeNutritionDto forRecipe(RecipeDto recipe) {
        Counted counted = count(recipe);
        return new RecipeNutritionDto(
                tiles(counted.total(), counted.servings()),
                counted.servings(),
                perServingKcal(counted),
                counted.used(),
                counted.lines(),
                counted.gaps());
    }

    // The planner's day, for Ela and Johann. A planned card is one serving, and each person eats their own
    // share of it (PlannerPerson.portion). Extras come on top: a dish by its servings (with the same share),
    // an ingredient by its amount, a custom snack as typed. Cards with no person are listed but count for nobody.
    @Transactional(readOnly = true)
    public DayNutritionDto forDay(Long weekId, DayOfWeek day) {
        Map<Long, Counted> countedByRecipe = new HashMap<>();
        Map<PlannerPerson, Map<Nutrient, Double>> byPerson = new EnumMap<>(PlannerPerson.class);
        List<DishNutritionDto> dishes = new ArrayList<>();
        List<NutritionGapDto> gaps = new ArrayList<>();
        int completeDishes = 0;
        int unassignedDishes = 0;
        boolean hasValues = false;

        for (PlannerEntry card : plannerEntryRepository.findAllByWeekIdOrderByIdAsc(weekId)) {
            if (card.getDayOfWeek() != day) continue;

            Counted counted = counted(countedByRecipe, card.getRecipe().getId());
            PlannerPerson person = card.getPerson();
            Map<Nutrient, Double> eaten = scaled(counted.total(),
                    (person == null ? 1 : person.portion()) / counted.servings());
            if (person == null) {
                unassignedDishes++;
            } else {
                addTo(byPerson, person, eaten);
            }
            dishes.add(new DishNutritionDto(card.getRecipe().getRecipeName(),
                    person == null ? null : person.label(), kcalOf(eaten), null));
            hasValues |= !eaten.isEmpty();
            gaps.addAll(counted.gaps());
            if (counted.gaps().isEmpty()) completeDishes++;
        }

        for (PlannerExtra extra : plannerExtraRepository.findAllByWeekIdAndDayOfWeekOrderByIdAsc(weekId, day)) {
            PlannerPerson person = extra.getPerson();
            Map<Nutrient, Double> eaten;
            List<NutritionGapDto> extraGaps = new ArrayList<>();
            String name;
            if (extra.getRecipe() != null) {
                Counted counted = counted(countedByRecipe, extra.getRecipe().getId());
                eaten = scaled(counted.total(), extra.getAmount() * person.portion() / counted.servings());
                extraGaps = counted.gaps();
                name = extra.getRecipe().getRecipeName() + " · " + plain(extra.getAmount())
                        + (extra.getAmount() == 1 ? " serving" : " servings");
            } else if (extra.getIngredient() != null) {
                Ingredient ingredient = extra.getIngredient();
                eaten = new EnumMap<>(Nutrient.class);
                addLine(ingredient.getId(), ingredient.getCanonicalName(), extra.getAmount(), extra.getUnit(),
                        ingredient.getNutrients(),
                        gramsPerUnit(Set.of(ingredient.getId())).getOrDefault(ingredient.getId(), Map.of()),
                        eaten, extraGaps);
                name = ingredient.getCanonicalName() + " · " + plain(extra.getAmount()) + " "
                        + (extra.getUnit().isEmpty() ? "piece" : extra.getUnit());
            } else {
                eaten = extra.getNutrients();
                name = extra.getName();
            }
            addTo(byPerson, person, eaten);
            dishes.add(new DishNutritionDto(name, person.label(), kcalOf(eaten), extra.getId()));
            hasValues |= !eaten.isEmpty();
            gaps.addAll(extraGaps);
            if (extraGaps.isEmpty()) completeDishes++;
        }

        return new DayNutritionDto(
                nutrientRows(byPerson.getOrDefault(PlannerPerson.ELA, Map.of()),
                        byPerson.getOrDefault(PlannerPerson.JOHANN, Map.of())),
                dishes,
                completeDishes,
                unassignedDishes,
                gaps.stream().distinct().toList(),
                hasValues);
    }

    private Counted counted(Map<Long, Counted> cache, Long recipeId) {
        return cache.computeIfAbsent(recipeId, id -> recipeService.findById(id).map(this::count).orElseThrow());
    }

    // Adds up one whole recipe. Lines that can't be counted are listed as gaps, so the numbers are
    // never shown as more complete than they are.
    private Counted count(RecipeDto recipe) {
        List<RecipeIngredientDto> lines = recipe.ingredients();
        Set<Long> ids = lines.stream().map(RecipeIngredientDto::ingredientId).collect(Collectors.toSet());

        Map<Long, Ingredient> ingredients = new HashMap<>();
        ingredientRepository.findAllById(ids).forEach(ingredient -> ingredients.put(ingredient.getId(), ingredient));
        Map<Long, Map<String, Double>> gramsPerUnit = gramsPerUnit(ids);

        Map<Nutrient, Double> total = new EnumMap<>(Nutrient.class);
        List<NutritionGapDto> gaps = new ArrayList<>();
        int used = 0;

        for (RecipeIngredientDto line : lines) {
            if (addLine(line.ingredientId(), line.ingredientName(), line.quantity(), line.unit(),
                    ingredients.get(line.ingredientId()).getNutrients(),
                    gramsPerUnit.getOrDefault(line.ingredientId(), Map.of()), total, gaps)) {
                used++;
            }
        }

        int servings = recipe.servings() == null ? 1 : Math.max(1, recipe.servings());
        return new Counted(total, servings, used, lines.size(), gaps);
    }

    // Adds one amount of one ingredient to total. False, with the reason added to gaps, when it can't be counted.
    private static boolean addLine(Long ingredientId, String ingredientName, Double quantity, String rawUnit,
                                   Map<Nutrient, Double> per100g, Map<String, Double> gramsPerUnit,
                                   Map<Nutrient, Double> total, List<NutritionGapDto> gaps) {
        if (quantity == null || quantity <= 0) {
            gaps.add(new NutritionGapDto(ingredientId, ingredientName, "no amount given", false));
            return false;
        }
        String unit = Units.normalize(rawUnit);
        Double grams = Units.weightInGrams(unit);
        if (grams == null) {
            grams = gramsPerUnit.get(unit);
        }
        if (grams == null) {
            String name = unit.isEmpty() ? "piece" : unit;
            gaps.add(new NutritionGapDto(ingredientId, ingredientName, "no grams for " + name, true));
            return false;
        }
        if (per100g.isEmpty()) {
            gaps.add(new NutritionGapDto(ingredientId, ingredientName, "no nutrition values", true));
            return false;
        }
        double lineGrams = quantity * grams;
        per100g.forEach((nutrient, value) -> total.merge(nutrient, value * lineGrams / 100, Double::sum));
        return true;
    }

    // Saved grams per unit, by ingredient id.
    private Map<Long, Map<String, Double>> gramsPerUnit(Set<Long> ingredientIds) {
        Map<Long, Map<String, Double>> gramsPerUnit = new HashMap<>();
        for (IngredientUnitWeight weight : weightRepository.findAllByIngredientIdIn(ingredientIds)) {
            gramsPerUnit.computeIfAbsent(weight.getIngredient().getId(), id -> new HashMap<>())
                    .put(weight.getUnit(), weight.getGrams());
        }
        return gramsPerUnit;
    }

    // One box per main nutrient: the per-serving amount, and the whole-dish amount under it.
    // The saturated fat and sugar numbers go on the sub line of fat and carbs.
    private static List<NutritionTileDto> tiles(Map<Nutrient, Double> total, int servings) {
        Map<Nutrient, Nutrient> partOf = Map.of(Nutrient.FAT, Nutrient.SATURATED_FAT, Nutrient.CARBS, Nutrient.SUGAR);
        List<NutritionTileDto> tiles = new ArrayList<>();
        for (Nutrient nutrient : Nutrient.values()) {
            if (nutrient.isPart()) continue;
            Nutrient part = partOf.get(nutrient);
            tiles.add(new NutritionTileDto(
                    nutrient.label(), amount(total, nutrient, servings), nutrient.unit(),
                    amount(total, nutrient, 1),
                    part == null ? null : part.label().replace("of which ", ""),
                    part == null ? null : amount(total, part, servings),
                    part == null ? null : part.unit()));
        }
        return tiles;
    }

    // One row per nutrient, in enum order, so each "of which" line sits right under its fat or carbs.
    private static List<NutritionRowDto> nutrientRows(Map<Nutrient, Double> ela, Map<Nutrient, Double> johann) {
        List<NutritionRowDto> rows = new ArrayList<>();
        for (Nutrient nutrient : Nutrient.values()) {
            rows.add(new NutritionRowDto(nutrient.label(), nutrient.unit(),
                    amount(ela, nutrient, 1), amount(johann, nutrient, 1), nutrient.isPart()));
        }
        return rows;
    }

    private static Map<Nutrient, Double> scaled(Map<Nutrient, Double> values, double factor) {
        Map<Nutrient, Double> result = new EnumMap<>(Nutrient.class);
        values.forEach((nutrient, amount) -> result.put(nutrient, amount * factor));
        return result;
    }

    private static void addTo(Map<PlannerPerson, Map<Nutrient, Double>> byPerson, PlannerPerson person,
                              Map<Nutrient, Double> eaten) {
        Map<Nutrient, Double> sum = byPerson.computeIfAbsent(person, p -> new EnumMap<>(Nutrient.class));
        eaten.forEach((nutrient, amount) -> sum.merge(nutrient, amount, Double::sum));
    }

    // Null when the recipe has no calorie values, so the page shows nothing instead of 0.
    private static String perServingKcal(Counted counted) {
        Double kcal = counted.total().get(Nutrient.KCAL);
        return kcal == null ? null : format(kcal / counted.servings());
    }

    private static String kcalOf(Map<Nutrient, Double> values) {
        Double kcal = values.get(Nutrient.KCAL);
        return kcal == null ? null : format(kcal);
    }

    // An amount as it was typed: 1, 0.5, 2.25.
    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String amount(Map<Nutrient, Double> total, Nutrient nutrient, int divideBy) {
        return format(total.getOrDefault(nutrient, 0.0) / divideBy);
    }

    // 725, 94.9, 1.1: whole numbers from 100 up, one decimal below.
    private static String format(double value) {
        return BigDecimal.valueOf(value)
                .setScale(value >= 100 ? 0 : 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    // One recipe's whole-dish totals, plus what could not be counted.
    private record Counted(Map<Nutrient, Double> total, int servings, int used, int lines,
                           List<NutritionGapDto> gaps) {
    }
}
