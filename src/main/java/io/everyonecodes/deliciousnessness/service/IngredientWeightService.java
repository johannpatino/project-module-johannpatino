package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.IngredientUnitDto;
import io.everyonecodes.deliciousnessness.dto.IngredientUnitsDto;
import io.everyonecodes.deliciousnessness.dto.IngredientUseDto;
import io.everyonecodes.deliciousnessness.dto.NutrientValueDto;
import io.everyonecodes.deliciousnessness.model.Ingredient;
import io.everyonecodes.deliciousnessness.model.IngredientUnitWeight;
import io.everyonecodes.deliciousnessness.model.Nutrient;
import io.everyonecodes.deliciousnessness.repository.IngredientRepository;
import io.everyonecodes.deliciousnessness.repository.IngredientUnitWeightRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

// The "Edit ingredients" page: grams per unit and nutrition per 100 g, for every ingredient recipes use.
@Service
public class IngredientWeightService {

    private static final String GRAMS_PREFIX = "grams:";
    private static final String NUTRITION_PREFIX = "nutrition:";

    private final IngredientRepository ingredientRepository;
    private final IngredientUnitWeightRepository weightRepository;

    public IngredientWeightService(IngredientRepository ingredientRepository,
                                   IngredientUnitWeightRepository weightRepository) {
        this.ingredientRepository = ingredientRepository;
        this.weightRepository = weightRepository;
    }

    // Every ingredient used in a recipe: most used first, then by name.
    @Transactional(readOnly = true)
    public List<IngredientUnitsDto> findAllInUse() {
        Map<Long, String> names = new HashMap<>();
        Map<Long, Set<Long>> recipesByIngredient = new HashMap<>();
        Map<Long, Map<String, Set<Long>>> recipesByUnit = new HashMap<>();
        for (IngredientUseDto use : ingredientRepository.findAllUses()) {
            names.put(use.ingredientId(), use.name());
            recipesByIngredient.computeIfAbsent(use.ingredientId(), id -> new HashSet<>()).add(use.recipeId());
            Map<String, Set<Long>> units = recipesByUnit.computeIfAbsent(use.ingredientId(), id -> new HashMap<>());
            // A line with no amount ("cilantro, to serve") needs no grams, so its unit isn't listed.
            if (use.quantity() != null) {
                units.computeIfAbsent(Units.normalize(use.unit()), unit -> new HashSet<>()).add(use.recipeId());
            }
        }

        Map<Long, Map<String, Double>> savedGrams = new HashMap<>();
        for (IngredientUnitWeight weight : weightRepository.findAll()) {
            savedGrams.computeIfAbsent(weight.getIngredient().getId(), id -> new HashMap<>())
                    .put(weight.getUnit(), weight.getGrams());
        }
        Map<Long, Ingredient> ingredients = new HashMap<>();
        ingredientRepository.findAllById(names.keySet()).forEach(ingredient -> ingredients.put(ingredient.getId(), ingredient));

        return names.keySet().stream()
                .map(id -> toDto(id, names.get(id), recipesByIngredient.get(id).size(), recipesByUnit.get(id),
                        savedGrams.getOrDefault(id, Map.of()), ingredients.get(id).getNutrients()))
                .sorted(Comparator.comparingInt(IngredientUnitsDto::recipeCount).reversed()
                        .thenComparing(IngredientUnitsDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private IngredientUnitsDto toDto(Long id, String name, int recipeCount, Map<String, Set<Long>> recipesByUnit,
                                     Map<String, Double> savedGrams, Map<Nutrient, Double> nutrients) {
        List<IngredientUnitDto> units = recipesByUnit.entrySet().stream()
                .map(entry -> {
                    String unit = entry.getKey();
                    Double builtIn = Units.weightInGrams(unit);
                    Double grams = builtIn != null ? builtIn : savedGrams.get(unit);
                    return new IngredientUnitDto(unit, unit.isEmpty() ? "piece (no unit)" : unit,
                            entry.getValue().size(), textOf(grams), builtIn != null);
                })
                .sorted(Comparator.comparingInt(IngredientUnitDto::recipeCount).reversed()
                        .thenComparing(IngredientUnitDto::unit))
                .toList();
        int withoutGrams = (int) units.stream().filter(unit -> unit.grams() == null).count();

        List<NutrientValueDto> values = Arrays.stream(Nutrient.values())
                .map(nutrient -> new NutrientValueDto(nutrient, textOf(nutrients.get(nutrient))))
                .toList();
        return new IngredientUnitsDto(id, name, recipeCount, units, values, withoutGrams, !nutrients.isEmpty());
    }

    // params has fields named "grams:<unit>" and "nutrition:<NUTRIENT>". A blank box removes the saved value.
    // Text that isn't a number (or is negative) is ignored, so a typo never deletes anything.
    @Transactional
    public void save(Long ingredientId, Map<String, String> params) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found"));

        for (Map.Entry<String, String> field : params.entrySet()) {
            String key = field.getKey();
            if (key.startsWith(GRAMS_PREFIX)) {
                saveGrams(ingredient, Units.normalize(key.substring(GRAMS_PREFIX.length())), field.getValue());
            } else if (key.startsWith(NUTRITION_PREFIX)) {
                saveNutrient(ingredient, key.substring(NUTRITION_PREFIX.length()), field.getValue());
            }
        }
    }

    private void saveGrams(Ingredient ingredient, String unit, String text) {
        if (Units.weightInGrams(unit) != null) return;

        Optional<IngredientUnitWeight> existing = weightRepository.findByIngredientIdAndUnit(ingredient.getId(), unit);
        if (isBlank(text)) {
            existing.ifPresent(weightRepository::delete);
            return;
        }
        Double grams = parse(text);
        if (grams == null || grams <= 0) return;

        if (existing.isPresent()) {
            existing.get().setGrams(grams);
        } else {
            weightRepository.save(new IngredientUnitWeight(ingredient, unit, grams));
        }
    }

    private void saveNutrient(Ingredient ingredient, String nutrientName, String text) {
        Nutrient nutrient;
        try {
            nutrient = Nutrient.valueOf(nutrientName);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (isBlank(text)) {
            ingredient.getNutrients().remove(nutrient);
            return;
        }
        Double value = parse(text);
        if (value == null || value < 0) return;
        ingredient.getNutrients().put(nutrient, value);
    }

    // Units used to be saved as typed ("tl", "łyżeczka"). Each saved spelling moves to the app's name for that
    // unit. If an ingredient has grams for that unit more than once, the first row entered is kept and the
    // others are removed. Running it again does nothing.
    @Transactional
    public void mergeUnitSpellings() {
        List<IngredientUnitWeight> weights = weightRepository.findAll().stream()
                .sorted(Comparator.comparing(IngredientUnitWeight::getId))
                .toList();

        Set<String> taken = new HashSet<>();
        List<IngredientUnitWeight> duplicates = new ArrayList<>();
        List<IngredientUnitWeight> renamed = new ArrayList<>();
        for (IngredientUnitWeight weight : weights) {
            String name = Units.normalize(weight.getUnit());
            if (taken.add(keyOf(weight.getIngredient().getId(), name))) {
                if (!name.equals(weight.getUnit())) renamed.add(weight);
            } else {
                duplicates.add(weight);
            }
        }
        // The duplicates go first, so their names are free before the renamed rows take them.
        weightRepository.deleteAll(duplicates);
        weightRepository.flush();
        for (IngredientUnitWeight weight : renamed) {
            weight.setUnit(Units.normalize(weight.getUnit()));
        }
    }

    private static String keyOf(Long ingredientId, String unit) {
        return ingredientId + "|" + unit;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    // "12,5" and "12.5" both work. Null when it isn't a number.
    private static Double parse(String text) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOf(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
