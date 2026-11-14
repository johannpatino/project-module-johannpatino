package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.DayNutritionDto;
import io.everyonecodes.deliciousnessness.model.*;
import io.everyonecodes.deliciousnessness.repository.*;
import io.everyonecodes.deliciousnessness.service.NutritionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The planner's day: each person eats one planned serving per card, and extras are added on top.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DayNutritionIT {

    private static final DayOfWeek DAY = DayOfWeek.MONDAY;

    @LocalServerPort
    private int port;

    @Autowired private IngredientRepository ingredients;
    @Autowired private IngredientUnitWeightRepository weights;
    @Autowired private RecipeRepository recipes;
    @Autowired private PlannerWeekRepository weeks;
    @Autowired private PlannerEntryRepository entries;
    @Autowired private NutritionService nutrition;

    private RestTestClient owner;
    private Long weekId;
    private Long recipeId;
    private Long bananaId;

    @BeforeEach
    void setUp() {
        owner = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("test", "test"))
                .build();

        // 100 kcal per 100 g. The dish uses 200 g and makes 2 servings, so one serving is 100 kcal.
        Ingredient oats = new Ingredient("oats-" + System.nanoTime());
        oats.getNutrients().put(Nutrient.KCAL, 100.0);
        ingredients.save(oats);
        Recipe porridge = new Recipe();
        porridge.setRecipeName("Porridge");
        porridge.setServings(2);
        porridge.setCookTimeMinutes(5);
        porridge.setInstructions("Cook.");
        porridge.addIngredient(new RecipeIngredient(oats, "oats", 200.0, "g", null));
        recipeId = recipes.save(porridge).getId();

        // A banana piece weighs 120 g, at 90 kcal per 100 g: 108 kcal.
        Ingredient banana = new Ingredient("banana-" + System.nanoTime());
        banana.getNutrients().put(Nutrient.KCAL, 90.0);
        bananaId = ingredients.save(banana).getId();
        weights.save(new IngredientUnitWeight(banana, "piece", 120));

        PlannerWeek week = weeks.save(new PlannerWeek(LocalDate.of(2100, 1, 4).plusWeeks(System.nanoTime() % 100_000)));
        weekId = week.getId();
        entries.save(new PlannerEntry(week, DAY, PlannerPerson.ELA, porridge));
        entries.save(new PlannerEntry(week, DAY, PlannerPerson.JOHANN, porridge));
        entries.save(new PlannerEntry(week, DAY, null, porridge));
    }

    @Test
    void eachPersonEatsTheirShareAndUnassignedCountsForNobody() {
        DayNutritionDto day = nutrition.forDay(weekId, DAY);
        assertEquals("100", day.rows().get(0).ela());
        assertEquals("100", day.rows().get(0).johann());
        assertEquals(1, day.unassignedDishes());
    }

    @Test
    void extrasAreAddedToThePerson() {
        addExtra("person=ELA&kind=dish&recipeId=" + recipeId + "&amount=2");
        addExtra("person=JOHANN&kind=ingredient&ingredientId=" + bananaId + "&amount=1&unit=piece");
        String html = addExtra("person=ELA&kind=snack&name=Chips&nutrition%3AKCAL=250&nutrition%3AFAT=");
        assertTrue(html.contains("Chips"));

        DayNutritionDto day = nutrition.forDay(weekId, DAY);
        // Ela: 100 planned + 2 servings x 100 + 250 chips. Johann: 100 planned + 108 banana.
        assertEquals("550", day.rows().get(0).ela());
        assertEquals("208", day.rows().get(0).johann());
    }

    @Test
    void anExtraCanBeRemovedAndGoesWithItsWeek() {
        addExtra("person=ELA&kind=snack&name=Chocolate&nutrition%3AKCAL=200");
        Long extraId = nutrition.forDay(weekId, DAY).dishes().stream()
                .filter(dish -> dish.extraId() != null).findFirst().orElseThrow().extraId();

        String html = owner.post().uri("/planner/extras/" + extraId + "/delete").exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertFalse(html.contains("Chocolate"));

        addExtra("person=ELA&kind=snack&name=Chocolate&nutrition%3AKCAL=200");
        weeks.deleteById(weekId);
        assertFalse(weeks.existsById(weekId));
    }

    @Test
    void anExtraWithMissingBoxesIsRefused() {
        post("person=ELA&kind=snack&name=").expectStatus().isBadRequest();
        post("person=ELA&kind=dish&amount=1").expectStatus().isBadRequest();
        post("kind=snack&name=Chips").expectStatus().isBadRequest();
        post("person=ELA&kind=snack&name=Chips&nutrition%3AKCAL=-5").expectStatus().isBadRequest();
    }

    private String addExtra(String form) {
        return post(form).expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    }

    private RestTestClient.ResponseSpec post(String form) {
        return owner.post().uri("/planner/weeks/" + weekId + "/days/" + DAY + "/extras")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange();
    }
}
