package io.everyonecodes.deliciousnessness.dto;

import java.util.List;

// Amounts are per serving. usedLines of totalLines ingredient lines are in the numbers.
// kcalPerServing is null when nothing was counted.
public record RecipeNutritionDto(
        List<NutritionTileDto> tiles,
        int servings,
        String kcalPerServing,
        int usedLines,
        int totalLines,
        List<NutritionGapDto> gaps) {

    public boolean hasValues() {
        return usedLines > 0;
    }

    public boolean isComplete() {
        return gaps.isEmpty();
    }
}
