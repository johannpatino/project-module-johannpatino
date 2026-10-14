package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.service.ShoppingListService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/planner/weeks/{weekId}/shopping")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @GetMapping
    public String shoppingList(@PathVariable Long weekId, Model model) {
        model.addAttribute("list", shoppingListService.open(weekId));
        return "shopping-list";
    }

    @PostMapping("/rebuild")
    public String rebuild(@PathVariable Long weekId) {
        shoppingListService.rebuild(weekId);
        return back(weekId);
    }

    @PostMapping("/clear")
    public String clear(@PathVariable Long weekId) {
        shoppingListService.clear(weekId);
        return back(weekId);
    }

    @PostMapping("/items")
    public String addItem(@PathVariable Long weekId,
                          @RequestParam String name,
                          @RequestParam(required = false) String amount) {
        shoppingListService.addItem(weekId, name, amount);
        return back(weekId);
    }

    @PostMapping("/items/{itemId}")
    public String updateItem(@PathVariable Long weekId,
                             @PathVariable Long itemId,
                             @RequestParam String name,
                             @RequestParam(required = false) String amount) {
        shoppingListService.updateItem(weekId, itemId, name, amount);
        return back(weekId);
    }

    @PostMapping("/items/{itemId}/delete")
    public String removeItem(@PathVariable Long weekId, @PathVariable Long itemId) {
        shoppingListService.removeItem(weekId, itemId);
        return back(weekId);
    }

    private String back(Long weekId) {
        return "redirect:/planner/weeks/" + weekId + "/shopping";
    }
}
