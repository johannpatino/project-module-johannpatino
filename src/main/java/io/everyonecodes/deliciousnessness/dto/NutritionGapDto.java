package io.everyonecodes.deliciousnessness.dto;

// An ingredient line left out of the nutrition. fixable = the user can fill it in on the ingredients page.
public record NutritionGapDto(Long ingredientId, String ingredientName, String reason, boolean fixable) {
}
