package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/categories")
public class CategoryWebController {

    private final CategoryService categoryService;

    public CategoryWebController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAllWithUsage());
        return "categories";
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name) {
        categoryService.rename(id, name);
        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        categoryService.delete(id);
        return "redirect:/categories";
    }
}