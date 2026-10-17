package io.everyonecodes.deliciousnessness.dto;

import io.everyonecodes.deliciousnessness.model.Nutrient;

// value is text, or null when not filled in.
public record NutrientValueDto(Nutrient nutrient, String value) {
}
