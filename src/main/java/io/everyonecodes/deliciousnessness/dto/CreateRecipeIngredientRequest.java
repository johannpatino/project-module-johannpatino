package io.everyonecodes.deliciousnessness.dto;

import io.everyonecodes.deliciousnessness.model.Language;

public record CreateRecipeIngredientRequest(
        Long ingredientId,
        String name,
        Language languageCode,
        Double quantity,
        String unit,
        String preparation,
        String section) {
}
