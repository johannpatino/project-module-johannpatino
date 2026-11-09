package io.everyonecodes.deliciousnessness.dto;

import java.util.List;

// The planner's day view. rows hold Ela and Johann side by side.
// completeDishes counts dishes with every ingredient counted. unassignedDishes are cards with no person:
// they are listed but count for nobody.
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
