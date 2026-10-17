package io.everyonecodes.deliciousnessness.dto;

// One ingredient line of one recipe (unit as typed, can be null). Quantity is null for "to taste" lines.
public record IngredientUseDto(Long ingredientId, String name, String unit, Double quantity, Long recipeId) {
}
