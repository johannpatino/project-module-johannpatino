package io.everyonecodes.deliciousnessness.dto;

import java.util.List;

// The planner's whole-day view. rows hold Ela, Johann and the whole day side by side.
// completeDishes counts dishes with every ingredient counted. unassignedDishes are cards with no person:
// they count in the day column only.
public record DayNutritionDto(
        List<NutritionRowDto> rows,
        List<DishNutritionDto> dishes,
        int completeDishes,
        int unassignedDishes,
        List<NutritionGapDto> gaps,
        boolean hasValues) {

    public boolean isComplete() {
        return gaps.isEmpty();
    }
}
