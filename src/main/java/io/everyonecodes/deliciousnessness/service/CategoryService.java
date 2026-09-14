package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.CategoryDto;
import io.everyonecodes.deliciousnessness.dto.CategoryUsageDto;
import io.everyonecodes.deliciousnessness.model.Category;
import io.everyonecodes.deliciousnessness.model.Recipe;
import io.everyonecodes.deliciousnessness.repository.CategoryRepository;
import io.everyonecodes.deliciousnessness.repository.RecipeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final RecipeRepository recipeRepository;

    public CategoryService(CategoryRepository categoryRepository, RecipeRepository recipeRepository) {
        this.categoryRepository = categoryRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional
    public List<CategoryUsageDto> findAllWithUsage() {
        return categoryRepository.findAllWithUsage();
    }

    @Transactional
    public void rename(Long id, String newName) {
        String normalised = newName.trim().toLowerCase();
        if (normalised.isBlank()) {
            return;
        }
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        Optional<Category> clash = categoryRepository.findByNameIgnoreCase(normalised);

        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            moveRecipes(category, clash.get());
            categoryRepository.delete(category);
            return;
        }
        category.setName(normalised);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.findById(id).ifPresent(category -> {
            moveRecipes(category, null);
            categoryRepository.delete(category);
        });
    }

    private void moveRecipes(Category from, Category into) {
        for (Recipe recipe : recipeRepository.findByCategoriesId(from.getId())) {
            recipe.getCategories().remove(from);
            if (into != null) {
                recipe.getCategories().add(into);
            }
        }
    }

    @Transactional
    public Category findOrCreate(String name) {
        String normalised = name.trim().toLowerCase();

        return categoryRepository.findByNameIgnoreCase(normalised)
                .orElseGet(() -> categoryRepository.save(new Category(normalised)));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findAll() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Set<Category> findOrCreateAll(Set<String> names) {
        Set<Category> categories = new LinkedHashSet<>();

        if (names == null) {
            return categories;
        }

        for (String name : names) {
            categories.add(findOrCreate(name));
        }
        return categories;
    }
}
