package io.everyonecodes.deliciousnessness.dto;

// One nutrient row of the day table. part = "of which" line, shown indented. Amounts are text, already formatted.
public record NutritionRowDto(String label, String unit, String ela, String johann, String day, boolean part) {
}
