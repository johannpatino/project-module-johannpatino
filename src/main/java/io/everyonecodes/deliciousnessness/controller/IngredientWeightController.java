package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.service.IngredientWeightService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/planner/ingredients")
public class IngredientWeightController {

    private final IngredientWeightService ingredientWeightService;

    public IngredientWeightController(IngredientWeightService ingredientWeightService) {
        this.ingredientWeightService = ingredientWeightService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("ingredients", ingredientWeightService.findAllInUse());
        return "planner-ingredients";
    }

    // The form fields are "grams:<unit>" and "nutrition:<NUTRIENT>", so the form sends them as a map.
    @PostMapping("/{id}")
    public String save(@PathVariable Long id, @RequestParam Map<String, String> params,
                       RedirectAttributes redirect) {
        ingredientWeightService.save(id, params);
        redirect.addFlashAttribute("savedId", id);
        return "redirect:/planner/ingredients#ingredient-" + id;
    }
}
