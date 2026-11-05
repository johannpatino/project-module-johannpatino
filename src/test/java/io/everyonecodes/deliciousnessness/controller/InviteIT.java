package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.model.Role;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import io.everyonecodes.deliciousnessness.repository.InviteRepository;
import io.everyonecodes.deliciousnessness.service.InviteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InviteIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SignupController signup;

    @Autowired
    private InviteService invites;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private AppUserRepository users;

    private RestTestClient anonymous() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void thereIsNoOpenSignupPage() {
        anonymous().get().uri("/signup").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void inviteLinkPageOpensWithoutLogin() {
        anonymous().get().uri("/signup/" + invites.create()).exchange().expectStatus().isOk();
        anonymous().get().uri("/signup/not-a-real-token").exchange().expectStatus().isOk();
    }

    @Test
    void inviteMakesOneGuestAndThenStopsWorking() {
        String token = invites.create();

        String result = signup.signup(token, "invited-guest", "long-enough-password", new ExtendedModelMap());
        assertThat(result).isEqualTo("redirect:/login?registered");
        assertThat(users.findByUsername("invited-guest").orElseThrow().effectiveRole()).isEqualTo(Role.GUEST);
        assertThat(invites.isUsable(token)).isFalse();

        ExtendedModelMap second = new ExtendedModelMap();
        assertThat(signup.signup(token, "second-guest", "long-enough-password", second)).isEqualTo("signup");
        assertThat(second.get("linkWorks")).isEqualTo(false);
        assertThat(users.existsByUsername("second-guest")).isFalse();
    }

    @Test
    void invalidTokenMakesNoAccount() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(signup.signup("made-up", "nobody-here", "long-enough-password", model)).isEqualTo("signup");
        assertThat(model.get("linkWorks")).isEqualTo(false);
        assertThat(users.existsByUsername("nobody-here")).isFalse();
    }

    @Test
    void takenUsernameDoesNotUseUpTheInvite() {
        String first = invites.create();
        signup.signup(first, "taken-name", "long-enough-password", new ExtendedModelMap());

        String second = invites.create();
        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(signup.signup(second, "Taken-Name", "long-enough-password", model)).isEqualTo("signup");
        assertThat(model.get("problem")).isEqualTo("That username is taken.");
        assertThat(invites.isUsable(second)).isTrue();
    }

    @Test
    void expiredInviteDoesNotWork() {
        String token = invites.create();
        // Every invite not yet used is expired, so this token is one of them whichever row it is.
        inviteRepository.findAll().stream()
                .filter(invite -> invite.getUsedAt() == null)
                .forEach(invite -> {
                    invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
                    inviteRepository.save(invite);
                });

        assertThat(invites.isUsable(token)).isFalse();
        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(signup.signup(token, "late-guest", "long-enough-password", model)).isEqualTo("signup");
        assertThat(model.get("linkWorks")).isEqualTo(false);
        assertThat(users.existsByUsername("late-guest")).isFalse();
    }
}
