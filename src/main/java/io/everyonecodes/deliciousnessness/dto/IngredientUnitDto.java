package io.everyonecodes.deliciousnessness.dto;

// grams is text ("240", "12.5"), or null when not filled in. fixed = g and kg, which can't be edited.
public record IngredientUnitDto(String unit, String label, int recipeCount, String grams, boolean fixed) {
}
