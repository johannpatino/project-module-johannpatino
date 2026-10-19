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
import io.everyonecodes.deliciousnessness.model.PlannerPerson;
import io.everyonecodes.deliciousnessness.repository.IngredientRepository;
import io.everyonecodes.deliciousnessness.repository.IngredientUnitWeightRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerEntryRepository;
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

    public NutritionService(RecipeService recipeService, IngredientRepository ingredientRepository,
                            IngredientUnitWeightRepository weightRepository,
                            PlannerEntryRepository plannerEntryRepository) {
        this.recipeService = recipeService;
        this.ingredientRepository = ingredientRepository;
        this.weightRepository = weightRepository;
        this.plannerEntryRepository = plannerEntryRepository;
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

    // The planner's whole day. Each card is one serving of its recipe, so the day's numbers add up
    // every dish's per-serving amounts, split into Ela, Johann and the whole day.
    @Transactional(readOnly = true)
    public DayNutritionDto forDay(Long weekId, DayOfWeek day) {
        Map<Long, Counted> countedByRecipe = new HashMap<>();
        Map<Nutrient, Double> total = new EnumMap<>(Nutrient.class);
        Map<PlannerPerson, Map<Nutrient, Double>> byPerson = new EnumMap<>(PlannerPerson.class);
        List<DishNutritionDto> dishes = new ArrayList<>();
        List<NutritionGapDto> gaps = new ArrayList<>();
        int completeDishes = 0;
        int unassignedDishes = 0;

        for (PlannerEntry card : plannerEntryRepository.findAllByWeekIdOrderByIdAsc(weekId)) {
            if (card.getDayOfWeek() != day) continue;

            Counted counted = countedByRecipe.computeIfAbsent(card.getRecipe().getId(),
                    id -> recipeService.findById(id).map(this::count).orElseThrow());
            PlannerPerson person = card.getPerson();

            addPerServing(total, counted);
            if (person == null) {
                unassignedDishes++;
            } else {
                addPerServing(byPerson.computeIfAbsent(person, p -> new EnumMap<Nutrient, Double>(Nutrient.class)),
                        counted);
            }

            dishes.add(new DishNutritionDto(card.getRecipe().getRecipeName(), labelOf(person),
                    perServingKcal(counted)));
            gaps.addAll(counted.gaps());
            if (counted.gaps().isEmpty()) completeDishes++;
        }

        Map<Nutrient, Double> ela = byPerson.getOrDefault(PlannerPerson.ELA, Map.of());
        Map<Nutrient, Double> johann = byPerson.getOrDefault(PlannerPerson.JOHANN, Map.of());
        return new DayNutritionDto(
                nutrientRows(ela, johann, total),
                dishes,
                completeDishes,
                unassignedDishes,
                gaps.stream().distinct().toList(),
                !total.isEmpty());
    }

    // Adds up one whole recipe. Lines that can't be counted are listed as gaps, so the numbers are
    // never shown as more complete than they are.
    private Counted count(RecipeDto recipe) {
        List<RecipeIngredientDto> lines = recipe.ingredients();
        Set<Long> ids = lines.stream().map(RecipeIngredientDto::ingredientId).collect(Collectors.toSet());

        Map<Long, Ingredient> ingredients = new HashMap<>();
        ingredientRepository.findAllById(ids).forEach(ingredient -> ingredients.put(ingredient.getId(), ingredient));
        Map<Long, Map<String, Double>> gramsPerUnit = new HashMap<>();
        for (IngredientUnitWeight weight : weightRepository.findAllByIngredientIdIn(ids)) {
            gramsPerUnit.computeIfAbsent(weight.getIngredient().getId(), id -> new HashMap<>())
                    .put(weight.getUnit(), weight.getGrams());
        }

        Map<Nutrient, Double> total = new EnumMap<>(Nutrient.class);
        List<NutritionGapDto> gaps = new ArrayList<>();
        int used = 0;

        for (RecipeIngredientDto line : lines) {
            if (line.quantity() == null || line.quantity() <= 0) {
                gaps.add(new NutritionGapDto(line.ingredientId(), line.ingredientName(), "no amount given", false));
                continue;
            }
            String unit = Units.normalize(line.unit());
            Double grams = Units.weightInGrams(unit);
            if (grams == null) {
                grams = gramsPerUnit.getOrDefault(line.ingredientId(), Map.of()).get(unit);
            }
            if (grams == null) {
                String name = unit.isEmpty() ? "piece" : unit;
                gaps.add(new NutritionGapDto(line.ingredientId(), line.ingredientName(), "no grams for " + name, true));
                continue;
            }
            Map<Nutrient, Double> per100g = ingredients.get(line.ingredientId()).getNutrients();
            if (per100g.isEmpty()) {
                gaps.add(new NutritionGapDto(line.ingredientId(), line.ingredientName(), "no nutrition values", true));
                continue;
            }

            double lineGrams = line.quantity() * grams;
            per100g.forEach((nutrient, value) -> total.merge(nutrient, value * lineGrams / 100, Double::sum));
            used++;
        }

        int servings = recipe.servings() == null ? 1 : Math.max(1, recipe.servings());
        return new Counted(total, servings, used, lines.size(), gaps);
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
    private static List<NutritionRowDto> nutrientRows(Map<Nutrient, Double> ela, Map<Nutrient, Double> johann,
                                                      Map<Nutrient, Double> day) {
        List<NutritionRowDto> rows = new ArrayList<>();
        for (Nutrient nutrient : Nutrient.values()) {
            rows.add(new NutritionRowDto(nutrient.label(), nutrient.unit(),
                    amount(ela, nutrient, 1), amount(johann, nutrient, 1), amount(day, nutrient, 1),
                    nutrient.isPart()));
        }
        return rows;
    }

    // A dish is one serving, so its whole-dish amounts are divided by the recipe's servings.
    private static void addPerServing(Map<Nutrient, Double> sum, Counted counted) {
        counted.total().forEach((nutrient, amount) -> sum.merge(nutrient, amount / counted.servings(), Double::sum));
    }

    // Null when the recipe has no calorie values, so the page shows nothing instead of 0.
    private static String perServingKcal(Counted counted) {
        Double kcal = counted.total().get(Nutrient.KCAL);
        return kcal == null ? null : format(kcal / counted.servings());
    }

    private static String labelOf(PlannerPerson person) {
        if (person == null) return null;
        String name = person.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
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
