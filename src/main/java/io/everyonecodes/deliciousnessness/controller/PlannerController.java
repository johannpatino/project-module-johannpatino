package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.PlannerRecipeDto;
import io.everyonecodes.deliciousnessness.model.Nutrient;
import io.everyonecodes.deliciousnessness.model.PlannerPerson;
import io.everyonecodes.deliciousnessness.model.Season;
import io.everyonecodes.deliciousnessness.service.CategoryService;
import io.everyonecodes.deliciousnessness.service.IngredientWeightService;
import io.everyonecodes.deliciousnessness.service.NutritionService;
import io.everyonecodes.deliciousnessness.service.PlannerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/planner")
public class PlannerController {

    private static final String SNACK = "snack";

    private final PlannerService plannerService;
    private final CategoryService categoryService;
    private final NutritionService nutritionService;

    public PlannerController(PlannerService plannerService, CategoryService categoryService,
                             NutritionService nutritionService) {
        this.plannerService = plannerService;
        this.categoryService = categoryService;
        this.nutritionService = nutritionService;
    }

    @GetMapping
    public String planner(Model model) {
        model.addAttribute("weeks", plannerService.findWeeks());
        model.addAttribute("plannerWeeks", plannerService.findWeekOptions());
        List<PlannerRecipeDto> recipeOptions = plannerService.findRecipeOptions();
        model.addAttribute("recipeOptions", recipeOptions);
        // The "Add extra" popup offers only the recipes in the "snack" category.
        Long snackId = categoryService.findIdByName(SNACK).orElse(null);
        model.addAttribute("snackRecipes", recipeOptions.stream()
                .filter(recipe -> recipe.categoryIds().contains(snackId))
                .toList());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("seasons", Season.values());
        model.addAttribute("nutrients", Nutrient.values());
        return "planner";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("lastPlanned", plannerService.findLastPlanned());
        model.addAttribute("pastWeeks", plannerService.findPastWeeks());
        return "planner-history";
    }

    @PostMapping("/add")
    public String add(@RequestParam(required = false) List<Long> weekIds,
                      @RequestParam(required = false) List<Long> recipeIds) {
        plannerService.addEntries(weekIds, recipeIds);
        return "redirect:/planner";
    }

    // Empty day and person put the card back among the unplaced cards of its week (or of weekId's week).
    @PostMapping("/entries/{id}/move")
    public String move(@PathVariable Long id,
                       @RequestParam(required = false) Long weekId,
                       @RequestParam(required = false) DayOfWeek day,
                       @RequestParam(required = false) PlannerPerson person,
                       @RequestParam(required = false) Integer position) {
        plannerService.moveEntry(id, weekId, day, person, position);
        return "redirect:/planner";
    }

    @PostMapping("/{id}/delete")
    public String removeEntry(@PathVariable Long id) {
        plannerService.removeEntry(id);
        return "redirect:/planner";
    }

    @PostMapping("/weeks")
    public String addWeek() {
        plannerService.addWeek();
        return "redirect:/planner";
    }

    @PostMapping("/weeks/{id}/clear")
    public String clearWeek(@PathVariable Long id) {
        plannerService.clearWeek(id);
        return "redirect:/planner";
    }

    @PostMapping("/weeks/{id}/delete")
    public String removeWeek(@PathVariable Long id) {
        plannerService.removeWeek(id);
        return "redirect:/planner";
    }

    // The "Add extra" popup. Answers with the day's nutrition again, so planner.js can show it straight away.
    // Snack values come as fields named "nutrition:<NUTRIENT>". A blank box means none of it.
    @PostMapping("/weeks/{weekId}/days/{day}/extras")
    public String addExtra(@PathVariable Long weekId, @PathVariable DayOfWeek day,
                           @RequestParam(required = false) PlannerPerson person,
                           @RequestParam(required = false) String kind,
                           @RequestParam(required = false) Long recipeId,
                           @RequestParam(required = false) Long ingredientId,
                           @RequestParam(required = false) Double amount,
                           @RequestParam(required = false) String unit,
                           @RequestParam(required = false) String name,
                           @RequestParam Map<String, String> params,
                           Model model) {
        Map<Nutrient, Double> nutrients = new EnumMap<>(Nutrient.class);
        for (Nutrient nutrient : Nutrient.values()) {
            String text = params.get("nutrition:" + nutrient.name());
            if (text == null || text.isBlank()) continue;
            Double value = IngredientWeightService.parse(text);
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a number: " + nutrient.label());
            }
            nutrients.put(nutrient, value);
        }
        plannerService.addExtra(weekId, day, person, kind, recipeId, ingredientId, amount, unit, name, nutrients);
        return dayNutrition(weekId, day, model);
    }

    @PostMapping("/extras/{id}/delete")
    public String removeExtra(@PathVariable Long id, Model model) {
        var weekAndDay = plannerService.removeExtra(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extra not found"));
        return dayNutrition(weekAndDay.getKey(), weekAndDay.getValue(), model);
    }

    // The nutrition popup under a day column. Returns an HTML fragment that planner.js puts into the popup.
    @GetMapping("/weeks/{weekId}/days/{day}/nutrition")
    public String dayNutrition(@PathVariable Long weekId, @PathVariable DayOfWeek day, Model model) {
        model.addAttribute("weekId", weekId);
        model.addAttribute("day", day);
        model.addAttribute("nutrition", nutritionService.forDay(weekId, day));
        return "fragments/day-nutrition :: totals";
    }
}
