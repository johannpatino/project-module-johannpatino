package io.everyonecodes.deliciousnessness.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Salt was taken off the nutrition list (the ingredient salt is unchanged). Its saved values are deleted
// once at startup, so they can't break loading. Running it again does nothing.
@Component
public class RemovedNutrientCleanup implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public RemovedNutrientCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.update("DELETE FROM ingredient_nutrient WHERE nutrient = 'SALT'");
    }
}
