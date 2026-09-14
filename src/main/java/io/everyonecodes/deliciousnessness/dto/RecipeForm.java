package io.everyonecodes.deliciousnessness.dto;

import io.everyonecodes.deliciousnessness.model.Language;
import io.everyonecodes.deliciousnessness.model.Season;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.AutoPopulatingList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class RecipeForm {

    private Long id;
    private String recipeName;
    private Integer servings;
    private Integer cookTimeMinutes;
    private String instructions;
    private String imageUrl;
    private String sourceUrl;
    private List<Long> categoryIds = new ArrayList<>();
    private String newCategories = "";
    private List<IngredientLine> ingredients = new AutoPopulatingList<>(IngredientLine.class);
    private List<Season> seasons = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class IngredientLine {
        private Long ingredientId;
        private String section;
        private String name;
        private Language languageCode;
        private Double quantity;
        private String unit;
        private String preparation;
    }

    public static RecipeForm from(RecipeDto recipe) {
        RecipeForm form = new RecipeForm();
        form.setId(recipe.id());
        form.setRecipeName(recipe.recipeName());
        form.setServings(recipe.servings());
        form.setCookTimeMinutes(recipe.cookTimeMinutes());
        form.setInstructions(recipe.instructions());
        form.setSeasons(new ArrayList<>(recipe.seasons()));
        form.setImageUrl(recipe.imageUrl());
        form.setSourceUrl(recipe.sourceUrl());
        form.setCategoryIds(recipe.categories()
                .stream().map(CategoryDto::id)
                .collect(Collectors.toCollection(ArrayList::new)));
        recipe.ingredients().forEach(dto -> {
            IngredientLine line = new IngredientLine();
            line.setIngredientId(dto.ingredientId());
            line.setSection(dto.section());
            line.setName(dto.ingredientName());
            line.setQuantity(dto.quantity());
            line.setUnit(dto.unit());
            line.setPreparation(dto.preparation());
            form.getIngredients().add(line);
        });
        return form;
    }

    public void padTo(int size) {
        while (ingredients.size() < size) {
            ingredients.add(new IngredientLine());
        }
    }

    public CreateRecipeRequest toRequest(Set<String> categoryNames) {
        List<CreateRecipeIngredientRequest> lines = ingredients.stream()
                .filter(line -> line.getName() != null && !line.getName().isBlank())
                .map(line -> new CreateRecipeIngredientRequest(
                        line.getIngredientId(), line.getName().trim(), line.getLanguageCode(),
                        line.getQuantity(), blankToNull(line.getUnit()),
                        blankToNull(line.getPreparation()), blankToNull(line.getSection())))
                .toList();

        // No language on the web form: ingredients are identified by id, so the only thing
        // a language would still decide is how a genuinely new ingredient name gets tagged.
        return new CreateRecipeRequest(recipeName, servings, cookTimeMinutes, instructions,
                new LinkedHashSet<>(seasons), imageUrl, sourceUrl, null, categoryNames, lines);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}