package io.everyonecodes.deliciousnessness.dto;

// One line of the day's list: a planned card, or an extra (then extraId is set, so it can be removed).
// person is "Ela" or "Johann", or null for a card without one. kcal is what it counts for that person
// (one plain serving for a card without a person), or null when it can't be counted.
public record DishNutritionDto(String name, String person, String kcal, Long extraId) {
}
