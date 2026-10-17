package io.everyonecodes.deliciousnessness.dto;

import java.util.List;

// unitsWithoutGrams = units still to fill in. hasNutrition = at least one nutrient has a value.
public record IngredientUnitsDto(
        Long id,
        String name,
        int recipeCount,
        List<IngredientUnitDto> units,
        List<NutrientValueDto> nutrients,
        int unitsWithoutGrams,
        boolean hasNutrition) {

    public boolean needsInformation() {
        return unitsWithoutGrams > 0 || !hasNutrition;
    }
}
