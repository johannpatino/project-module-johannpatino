package io.everyonecodes.deliciousnessness.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class UnitSpellingMigration implements CommandLineRunner {

    private final IngredientWeightService ingredientWeightService;

    public UnitSpellingMigration(IngredientWeightService ingredientWeightService) {
        this.ingredientWeightService = ingredientWeightService;
    }

    @Override
    public void run(String... args) {
        ingredientWeightService.mergeUnitSpellings();
    }
}
