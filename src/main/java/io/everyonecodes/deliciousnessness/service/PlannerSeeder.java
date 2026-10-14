package io.everyonecodes.deliciousnessness.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlannerSeeder implements CommandLineRunner {

    private final PlannerService plannerService;
    private final JdbcTemplate jdbcTemplate;

    public PlannerSeeder(PlannerService plannerService, JdbcTemplate jdbcTemplate) {
        this.plannerService = plannerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // Hibernate's ddl-auto=update can't relax a NOT NULL column that already exists, and unplaced
        // cards have no day. Running this again does nothing.
        jdbcTemplate.execute("ALTER TABLE planner_entry ALTER COLUMN day_of_week DROP NOT NULL");
        plannerService.ensureDefaultWeek();
    }
}
