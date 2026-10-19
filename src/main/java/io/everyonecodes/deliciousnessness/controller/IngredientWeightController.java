package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.service.IngredientWeightService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Controller
@RequestMapping("/planner/ingredients")
public class IngredientWeightController {

    private final IngredientWeightService ingredientWeightService;

    public IngredientWeightController(IngredientWeightService ingredientWeightService) {
        this.ingredientWeightService = ingredientWeightService;
    }

    // q is the search text, missing is the "only ingredients that need information" tick box.
    @GetMapping
    public String page(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Boolean missing,
                       Model model) {
        model.addAttribute("ingredients", ingredientWeightService.findAllInUse());
        model.addAttribute("searchText", q);
        model.addAttribute("onlyMissing", Boolean.TRUE.equals(missing));
        return "planner-ingredients";
    }

    // The form fields are "grams:<unit>" and "nutrition:<NUTRIENT>", so the form sends them as a map.
    // Saving brings the search and the tick box back, so the list stays as the user left it.
    @PostMapping("/{id}")
    public String save(@PathVariable Long id,
                       @RequestParam Map<String, String> params,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) Boolean missing,
                       RedirectAttributes redirect) {
        ingredientWeightService.save(id, params);
        redirect.addFlashAttribute("savedId", id);

        UriComponentsBuilder back = UriComponentsBuilder.fromPath("/planner/ingredients");
        if (Boolean.TRUE.equals(missing)) back.queryParam("missing", true);
        if (q != null && !q.isBlank()) back.queryParam("q", q.trim());
        back.fragment("ingredient-" + id);
        return "redirect:" + back.build().encode().toUriString();
    }
}
