package io.everyonecodes.deliciousnessness.dto;

import io.everyonecodes.deliciousnessness.model.Season;

import java.util.List;

public record PlannerRecipeDto(
        Long id,
        String recipeName,
        String imageUrl,
        List<Long> categoryIds,
        List<Season> seasons) {
}
