package io.everyonecodes.deliciousnessness.dto;

import java.time.LocalDate;

public record PastRecipeDto(Long recipeId, String recipeName, LocalDate lastPlanned) {
}
