package io.everyonecodes.deliciousnessness.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ShoppingLineMigration implements CommandLineRunner {

    private final ShoppingListService shoppingListService;

    public ShoppingLineMigration(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @Override
    public void run(String... args) {
        shoppingListService.linkSavedLines();
    }
}
