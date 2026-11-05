package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.CreateRecipeRequest;
import io.everyonecodes.deliciousnessness.security.LoginThrottle;
import io.everyonecodes.deliciousnessness.security.WebLinks;
import io.everyonecodes.deliciousnessness.service.InviteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHardeningIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SignupController signup;

    @Autowired
    private InviteService invites;

    @Autowired
    private LoginThrottle throttle;

    private RestTestClient clientFor(String username, String password) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }

    @Test
    void onlyHttpAndHttpsLinksAreSafe() {
        assertThat(WebLinks.isSafe(null)).isTrue();
        assertThat(WebLinks.isSafe("  ")).isTrue();
        assertThat(WebLinks.isSafe("https://example.com/a.jpg")).isTrue();
        assertThat(WebLinks.isSafe("http://example.com")).isTrue();
        assertThat(WebLinks.isSafe("javascript:alert(1)")).isFalse();
        assertThat(WebLinks.isSafe("JaVaScRiPt:alert(1)")).isFalse();
        assertThat(WebLinks.isSafe("data:text/html,<script>alert(1)</script>")).isFalse();
        assertThat(WebLinks.isSafe("//example.com/a.jpg")).isFalse();
        assertThat(WebLinks.isSafe("/relative.jpg")).isFalse();
    }

    @Test
    void recipeWithAScriptLinkIsRejected() {
        CreateRecipeRequest request = new CreateRecipeRequest("Bad", 1, 1, "x", Set.of(),
                null, "javascript:alert(1)", null, Set.of(), List.of());
        clientFor("test", "test").post().uri("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void ownerNameCannotBeSignedUpInAnyCase() {
        for (String name : List.of("test", "TEST", " Test ")) {
            ExtendedModelMap model = new ExtendedModelMap();
            String token = invites.create();
            assertThat(signup.signup(token, name, "long-enough-password", model)).isEqualTo("signup");
            assertThat(model.get("problem")).isEqualTo("That username is taken.");
            assertThat(invites.isUsable(token)).isTrue();
        }
    }

    @Test
    void repeatedWrongPasswordsLockThatUsernameOut() {
        RestTestClient attacker = clientFor("someone-throttled", "wrong");
        for (int i = 0; i < 8; i++) {
            attacker.get().uri("/recipes").exchange().expectStatus().isUnauthorized();
        }
        attacker.get().uri("/recipes").exchange().expectStatus().isEqualTo(429);

        clientFor("test", "test").get().uri("/recipes").exchange().expectStatus().isOk();
    }

    @AfterEach
    void clearTheThrottle() {
        throttle.reset();
    }

    @Test
    void anAddressThatGuessesManyUsernamesIsBlockedEvenForRealLogins() {
        for (int i = 0; i < 30; i++) {
            clientFor("made-up-name-" + i, "wrong").get().uri("/recipes").exchange().expectStatus().isUnauthorized();
        }

        clientFor("test", "test").get().uri("/recipes").exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void responsesCarryTheSecurityHeaders() {
        RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri("/login").exchange()
                .expectStatus().isOk()
                .expectHeader().value("Content-Security-Policy",
                        csp -> assertThat(csp).contains("script-src 'self'", "frame-ancestors 'none'"))
                .expectHeader().exists("X-Content-Type-Options")
                .expectHeader().exists("Referrer-Policy");
    }
}
