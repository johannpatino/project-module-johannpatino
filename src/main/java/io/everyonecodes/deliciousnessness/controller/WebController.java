package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.CategoryDto;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.dto.RecipeForm;
import io.everyonecodes.deliciousnessness.model.Language;
import io.everyonecodes.deliciousnessness.model.Season;
import io.everyonecodes.deliciousnessness.service.CategoryService;
import io.everyonecodes.deliciousnessness.service.IngredientService;
import io.everyonecodes.deliciousnessness.service.RecipeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/recipes")
public class WebController {

    private final RecipeService recipeService;
    private final CategoryService categoryService;
    private static final int MIN_INGREDIENT_ROWS = 3;
    private final IngredientService ingredientService;

    public WebController(RecipeService recipeService, CategoryService categoryService, IngredientService ingredientService) {
        this.recipeService = recipeService;
        this.categoryService = categoryService;
        this.ingredientService = ingredientService;
    }


    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) List<Long> ingredients,
                       @RequestParam(required = false) List<Long> categories,
                       @RequestParam(required = false) List<Season> seasons,
                       Model model) {
        populateSearchModel(q, ingredients, categories, seasons, model);
        return "index";
    }

    @GetMapping("/fragment")
    public String listFragment(@RequestParam(required = false) String q,
                               @RequestParam(required = false) List<Long> ingredients,
                               @RequestParam(required = false) List<Long> categories,
                               @RequestParam(required = false) List<Season> seasons,
                               Model model) {
        populateSearchModel(q, ingredients, categories, seasons, model);
        return "index :: results";
    }

    private void populateSearchModel(String q, List<Long> ingredients, List<Long> categories,
                                     List<Season> seasons, Model model) {
        model.addAttribute("recipes", recipeService.search(q, ingredients, categories, seasons));
        model.addAttribute("allCategories", categoryService.findAll());
        model.addAttribute("seasons", Season.values());
        model.addAttribute("q", q);
        model.addAttribute("selectedCategories", categories == null ? List.of() : categories);
        model.addAttribute("selectedSeasons", seasons == null ? List.of() : seasons);
        model.addAttribute("selectedIngredients",
                ingredientService.findByIds(ingredients == null ? List.of() : ingredients));
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        RecipeDto recipe = recipeService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
        model.addAttribute("recipe", recipe);
        return "recipe-detail";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        recipeService.deleteById(id);
        return "redirect:/recipes";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        RecipeForm form = new RecipeForm();
        form.padTo(MIN_INGREDIENT_ROWS);
        model.addAttribute("form", form);
        addFormOptions(model);
        return "recipe-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        RecipeDto recipe = recipeService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));

        RecipeForm form = RecipeForm.from(recipe);
        form.padTo(form.getIngredients().size() + 4);
        model.addAttribute("form", form);
        addFormOptions(model);
        return "recipe-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("form") RecipeForm form) {
        Set<String> categoryNames = resolveCategoryNames(form);

        Long id = (form.getId() == null)
                ? recipeService.create(form.toRequest(categoryNames)).id()
                : recipeService.update(form.getId(), form.toRequest(categoryNames))
                .map(RecipeDto::id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));

        return "redirect:/recipes/" + id;
    }

    private Set<String> resolveCategoryNames(RecipeForm form) {
        Set<String> names = new LinkedHashSet<>();

        categoryService.findAll().stream()
                .filter(category -> form.getCategoryIds().contains(category.id()))
                .map(CategoryDto::name)
                .forEach(names::add);

        if (form.getNewCategories() != null) {
            Arrays.stream(form.getNewCategories().split(","))
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .forEach(names::add);
        }
        return names;
    }

    private void addFormOptions(Model model) {
        model.addAttribute("seasons", Season.values());
        model.addAttribute("languages", Language.values());
        model.addAttribute("allCategories", categoryService.findAll());
    }
}
