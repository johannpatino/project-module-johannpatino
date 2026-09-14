package io.everyonecodes.deliciousnessness.mapper;

import io.everyonecodes.deliciousnessness.dto.CategoryDto;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.dto.RecipeIngredientDto;
import io.everyonecodes.deliciousnessness.model.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RecipeMapper {
    public RecipeDto toDto(Recipe recipe) {
        Set<CategoryDto> categories = recipe.getCategories()
                .stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toSet());

        List<RecipeIngredientDto> ingredients = recipe.getRecipeIngredients()
                .stream().map(this::toIngredientDto)
                .toList();
        Set<Season> seasons = new LinkedHashSet<>(recipe.getSeasons());
        return new RecipeDto(
                recipe.getId(),
                recipe.getRecipeName(),
                recipe.getServings(),
                recipe.getCookTimeMinutes(),
                recipe.getInstructions(),
                seasons,
                recipe.getImageUrl(),
                recipe.getSourceUrl(),
                recipe.getCreatedAt(),
                categories,
                ingredients);
    }

    public CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }

    public RecipeIngredientDto toIngredientDto(RecipeIngredient recipeIngredient) {
        Ingredient ingredient = recipeIngredient.getIngredient();
        String displayName = recipeIngredient.getDisplayName();

        return new RecipeIngredientDto(
                ingredient.getId(),
                (displayName == null || displayName.isBlank())
                        ? ingredient.getCanonicalName()
                        : displayName,
                recipeIngredient.getQuantity(),
                recipeIngredient.getUnit(),
                recipeIngredient.getPreparation(),
                recipeIngredient.getSection());
    }
}
