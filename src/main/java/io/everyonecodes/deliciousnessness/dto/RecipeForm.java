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

    // The database column sizes. A longer value would fail the save, so the form is shown again instead.
    private static final int RECIPE_NAME_MAX = 255;
    private static final int URL_MAX = 2048;
    private static final int CATEGORY_MAX = 64;
    private static final int INGREDIENT_NAME_MAX = 128;
    private static final int UNIT_MAX = 32;
    private static final int PREPARATION_MAX = 128;
    private static final int SECTION_MAX = 64;

    // Everything that is too long for its column. Empty when the form can be saved.
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        tooLong(problems, "The recipe name", recipeName, RECIPE_NAME_MAX);
        tooLong(problems, "The image link", imageUrl, URL_MAX);
        tooLong(problems, "The source link", sourceUrl, URL_MAX);
        if (newCategories != null) {
            for (String category : newCategories.split(",")) {
                tooLong(problems, "A category name", category.trim(), CATEGORY_MAX);
            }
        }
        for (IngredientLine line : ingredients) {
            tooLong(problems, "An ingredient name", line.getName(), INGREDIENT_NAME_MAX);
            tooLong(problems, "A unit", line.getUnit(), UNIT_MAX);
            tooLong(problems, "A preparation", line.getPreparation(), PREPARATION_MAX);
            tooLong(problems, "A section name", line.getSection(), SECTION_MAX);
        }
        return problems.stream().distinct().toList();
    }

    private static void tooLong(List<String> problems, String label, String value, int max) {
        if (value != null && value.length() > max) {
            problems.add(label + " is longer than " + max + " characters. Shorten it and save again.");
        }
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