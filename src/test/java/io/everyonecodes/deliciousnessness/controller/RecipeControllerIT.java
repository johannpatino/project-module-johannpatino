package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.CreateRecipeIngredientRequest;
import io.everyonecodes.deliciousnessness.dto.CreateRecipeRequest;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.model.Language;
import io.everyonecodes.deliciousnessness.model.Season;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RecipeControllerIT {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void createsRecipeWithNestedDataAndReadsItBack() {
        CreateRecipeRequest request = new CreateRecipeRequest(
                "Pasta Carbonara",
                4,
                30,
                "1. Cook spaghetti. 2. Fry guanciale. 3. Mix off the heat.",
                Set.of(Season.SUMMER),
                null,
                null,
                Language.EN,
                Set.of("pasta", "dinner"),
                List.of(
                        new CreateRecipeIngredientRequest(null, "spaghetti", null, 400.0, "g", null, null),
                        new CreateRecipeIngredientRequest(null, "guanciale", null, 150.0, "g", "diced", null),
                        new CreateRecipeIngredientRequest(null, "pecorino", null, 50.0, "g", "grated", null)));

        RecipeDto created = client.post()
                .uri("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(RecipeDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.categories()).hasSize(2);
        assertThat(created.ingredients()).hasSize(3);

        client.get()
                .uri("/api/recipes/" + created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecipeDto.class)
                .value(fetched -> {
                    assertThat(fetched.recipeName()).isEqualTo("Pasta Carbonara");
                    assertThat(fetched.categories()).hasSize(2);
                    assertThat(fetched.ingredients()).hasSize(3);
                    assertThat(fetched.ingredients())
                            .extracting(dto -> dto.ingredientName())
                            .containsExactlyInAnyOrder("spaghetti", "guanciale", "pecorino");
                });
    }
}