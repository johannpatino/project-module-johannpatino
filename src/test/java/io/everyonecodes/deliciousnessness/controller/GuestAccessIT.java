package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Role;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GuestAccessIT {

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private RestTestClient guest;
    private RestTestClient user;

    @BeforeEach
    void setUp() {
        if (!users.existsByUsername("guest-test")) {
            users.save(new AppUser("guest-test", encoder.encode("guest-pass"), Role.GUEST));
        }
        guest = clientFor("guest-test", "guest-pass");
        user = clientFor("test", "test");
    }

    private RestTestClient clientFor(String username, String password) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }

    @Test
    void guestCanBrowseSearchAndFilter() {
        guest.get().uri("/recipes").exchange().expectStatus().isOk();
        guest.get().uri("/recipes/fragment?q=soup").exchange().expectStatus().isOk();
        guest.get().uri("/api/categories").exchange().expectStatus().isOk();
        guest.get().uri("/api/ingredients/autocomplete?q=on").exchange().expectStatus().isOk();
    }

    @Test
    void guestCannotReachThePlannerOrTheRecipeForms() {
        guest.get().uri("/planner").exchange().expectStatus().isForbidden();
        guest.get().uri("/planner/history").exchange().expectStatus().isForbidden();
        guest.get().uri("/recipes/new").exchange().expectStatus().isForbidden();
        guest.get().uri("/recipes/1/edit").exchange().expectStatus().isForbidden();
    }

    @Test
    void guestCannotChangeAnything() {
        guest.post().uri("/recipes/save").exchange().expectStatus().isForbidden();
        guest.post().uri("/recipes/1/delete").exchange().expectStatus().isForbidden();
        guest.post().uri("/planner/add").exchange().expectStatus().isForbidden();
        guest.post().uri("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange().expectStatus().isForbidden();
        guest.delete().uri("/api/recipes/1").exchange().expectStatus().isForbidden();
    }

    @Test
    void userKeepsFullAccess() {
        user.get().uri("/planner").exchange().expectStatus().isOk();
        user.get().uri("/recipes/new").exchange().expectStatus().isOk();
    }

    @Test
    void guestCannotReachTheInvitePage() {
        guest.get().uri("/invites").exchange().expectStatus().isForbidden();
        guest.post().uri("/invites").exchange().expectStatus().isForbidden();
        // The invites live on the profile page. The old address only passes the owner on to it.
        user.get().uri("/invites").exchange().expectStatus().is3xxRedirection();
    }
}
