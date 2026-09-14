package io.everyonecodes.deliciousnessness.dto;

public record RecipeIngredientDto(
        Long ingredientId,
        String ingredientName,
        Double quantity,
        String unit,
        String preparation,
        String section) {
}
