package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.model.PlannerPerson;
import io.everyonecodes.deliciousnessness.model.Season;
import io.everyonecodes.deliciousnessness.service.CategoryService;
import io.everyonecodes.deliciousnessness.service.NutritionService;
import io.everyonecodes.deliciousnessness.service.PlannerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;

@Controller
@RequestMapping("/planner")
public class PlannerController {

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
        model.addAttribute("recipeOptions", plannerService.findRecipeOptions());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("seasons", Season.values());
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

    // The nutrition popup under a day column. Returns an HTML fragment that planner.js puts into the popup.
    @GetMapping("/weeks/{weekId}/days/{day}/nutrition")
    public String dayNutrition(@PathVariable Long weekId, @PathVariable DayOfWeek day, Model model) {
        model.addAttribute("day", day);
        model.addAttribute("nutrition", nutritionService.forDay(weekId, day));
        return "fragments/day-nutrition :: totals";
    }
}
