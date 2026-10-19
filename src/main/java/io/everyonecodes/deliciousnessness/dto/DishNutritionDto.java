package io.everyonecodes.deliciousnessness.dto;

// One card on the day. person is "Ela" or "Johann", or null for a card without one.
// kcalPerServing is null when the dish can't be counted.
public record DishNutritionDto(String name, String person, String kcalPerServing) {
}
