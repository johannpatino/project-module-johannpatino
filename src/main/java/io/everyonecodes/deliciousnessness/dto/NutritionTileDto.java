package io.everyonecodes.deliciousnessness.dto;

// One box on the recipe page: the amount per serving, and the amount for the whole dish.
// The sub line is the "of which" part (fat -> saturated) per serving, or null.
public record NutritionTileDto(String label, String amount, String unit, String wholeDish,
                               String subLabel, String subAmount, String subUnit) {
}
