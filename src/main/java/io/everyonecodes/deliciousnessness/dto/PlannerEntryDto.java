package io.everyonecodes.deliciousnessness.dto;

// breakfast = the recipe has the "breakfast" tag. Breakfast cards are listed first in each person's column.
public record PlannerEntryDto(Long id, Long recipeId, String recipeName, String imageUrl, boolean breakfast) {
}
