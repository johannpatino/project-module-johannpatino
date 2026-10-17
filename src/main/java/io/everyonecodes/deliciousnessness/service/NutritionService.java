package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.NutritionGapDto;
import io.everyonecodes.deliciousnessness.dto.NutritionTileDto;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.dto.RecipeIngredientDto;
import io.everyonecodes.deliciousnessness.dto.RecipeNutritionDto;
import io.everyonecodes.deliciousnessness.model.Ingredient;
import io.everyonecodes.deliciousnessness.model.IngredientUnitWeight;
import io.everyonecodes.deliciousnessness.model.Nutrient;
import io.everyonecodes.deliciousnessness.repository.IngredientRepository;
import io.everyonecodes.deliciousnessness.repository.IngredientUnitWeightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

// Adds up the nutrition of a recipe from its ingredient lines:
// quantity x grams per unit = grams, then grams / 100 x the ingredient's values per 100 g.
@Service
public class NutritionService {

    private final IngredientRepository ingredientRepository;
    private final IngredientUnitWeightRepository weightRepository;

    public NutritionService(IngredientRepository ingredientRepository,
                            IngredientUnitWeightRepository weightRepository) {
        this.ingredientRepository = ingredientRepository;
        this.weightRepository = weightRepository;
    }

    // Lines that can't be counted (no amount, no grams for the unit, no nutrition values) are listed
    // as gaps, so the numbers are never shown as more complete than they are.
    @Transactional(readOnly = true)
    public RecipeNutritionDto forRecipe(RecipeDto recipe) {
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
        String kcal = used == 0 ? null : format(total.getOrDefault(Nutrient.KCAL, 0.0) / servings);
        return new RecipeNutritionDto(tiles(total, servings), servings, kcal, used, lines.size(), gaps);
    }

    // One box per main nutrient, with the per-serving amount and the whole-dish amount.
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
}
