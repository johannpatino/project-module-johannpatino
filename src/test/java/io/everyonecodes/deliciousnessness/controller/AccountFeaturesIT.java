package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.CreateRecipeRequest;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Comment;
import io.everyonecodes.deliciousnessness.model.Role;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import io.everyonecodes.deliciousnessness.repository.CommentRepository;
import io.everyonecodes.deliciousnessness.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountFeaturesIT {

    private static final String PASSWORD = "a-long-test-password";

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private CommentRepository comments;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AccountService accounts;

    private RestTestClient owner;

    @BeforeEach
    void setUp() {
        owner = clientFor("test", "test");
    }

    private RestTestClient clientFor(String username, String password) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }

    private AppUser guest(String name) {
        return users.save(new AppUser(name, encoder.encode(PASSWORD), Role.GUEST));
    }

    private Long recipe() {
        CreateRecipeRequest request = new CreateRecipeRequest("Comment target", 1, 1, "x", Set.of(),
                null, null, null, Set.of(), List.of());
        return owner.post().uri("/api/recipes").contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange().expectStatus().isCreated()
                .expectBody(RecipeDto.class).returnResult().getResponseBody().id();
    }

    private void postForm(RestTestClient client, String uri, String form) {
        client.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .exchange().expectStatus().is3xxRedirection();
    }

    // ---- not logged in ----

    @Test
    void visitorWhoIsNotLoggedInSeesThePrivateWebsitePage() {
        RestTestClient visitor = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        visitor.get().uri("/recipes").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", location -> assertThat(location).endsWith("/private"));
        visitor.get().uri("/recipes").exchange().expectStatus().isUnauthorized();
        visitor.get().uri("/private").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("don't have access"));
        visitor.get().uri("/robots.txt").exchange().expectStatus().isOk();
    }

    // ---- comments ----

    @Test
    void guestCanCommentAndSeeTheCommentOnTheRecipePage() {
        guest("commenter-one");
        RestTestClient guest = clientFor("commenter-one", PASSWORD);
        Long id = recipe();

        postForm(guest, "/recipes/" + id + "/comments", "body=Lovely+with+extra+garlic");

        guest.get().uri("/recipes/" + id).exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("Lovely with extra garlic").contains("commenter-one"));
    }

    @Test
    void commentTextIsEscapedNotRunAsHtml() {
        guest("commenter-xss");
        RestTestClient guest = clientFor("commenter-xss", PASSWORD);
        Long id = recipe();

        postForm(guest, "/recipes/" + id + "/comments", "body=%3Cscript%3Ealert(1)%3C%2Fscript%3E");

        guest.get().uri("/recipes/" + id).exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;"));
    }

    @Test
    void emptyAndTooLongCommentsAreNotSaved() {
        guest("commenter-empty");
        RestTestClient guest = clientFor("commenter-empty", PASSWORD);
        Long id = recipe();

        postForm(guest, "/recipes/" + id + "/comments", "body=+++");
        postForm(guest, "/recipes/" + id + "/comments", "body=" + "a".repeat(Comment.MAX_LENGTH + 1));

        assertThat(comments.findForRecipe(id)).isEmpty();
    }

    @Test
    void onlyTheAuthorOrTheOwnerCanDeleteAComment() {
        guest("author-guest");
        guest("other-guest");
        RestTestClient author = clientFor("author-guest", PASSWORD);
        RestTestClient other = clientFor("other-guest", PASSWORD);
        Long id = recipe();

        postForm(author, "/recipes/" + id + "/comments", "body=first");
        Long commentId = comments.findForRecipe(id).get(0).getId();

        postForm(other, "/comments/" + commentId + "/delete", "");
        assertThat(comments.findById(commentId)).isPresent();

        postForm(author, "/comments/" + commentId + "/delete", "");
        assertThat(comments.findById(commentId)).isEmpty();

        postForm(author, "/recipes/" + id + "/comments", "body=second");
        Long second = comments.findForRecipe(id).get(0).getId();
        postForm(owner, "/comments/" + second + "/delete", "");
        assertThat(comments.findById(second)).isEmpty();
    }

    @Test
    void deletingARecipeDeletesItsComments() {
        guest("commenter-cascade");
        Long id = recipe();
        postForm(clientFor("commenter-cascade", PASSWORD), "/recipes/" + id + "/comments", "body=bye");
        assertThat(comments.findForRecipe(id)).hasSize(1);

        owner.delete().uri("/api/recipes/" + id).exchange().expectStatus().isNoContent();

        assertThat(comments.findForRecipe(id)).isEmpty();
    }

    // ---- profile ----

    @Test
    void profilePageOpensForGuestAndOwner() {
        guest("profile-viewer");
        clientFor("profile-viewer", PASSWORD).get().uri("/profile").exchange().expectStatus().isOk();
        owner.get().uri("/profile").exchange().expectStatus().isOk();
    }

    @Test
    void invitesAreOnTheProfilePageForTheOwnerOnly() {
        guest("profile-guest");
        owner.get().uri("/profile").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("Create invite link").contains("Log out"));
        clientFor("profile-guest", PASSWORD).get().uri("/profile").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .doesNotContain("Create invite link").contains("Log out"));
    }

    @Test
    void passwordChangeNeedsTheCurrentPasswordAndALongNewOne() {
        guest("pw-changer");
        RestTestClient me = clientFor("pw-changer", PASSWORD);
        String mismatch = "&newPassword=brand-new-long-pass&confirmPassword=different-long-pass";

        postForm(me, "/profile/password", "currentPassword=wrong-password-here" + mismatch);
        postForm(me, "/profile/password", "currentPassword=" + PASSWORD + mismatch);
        postForm(me, "/profile/password",
                "currentPassword=" + PASSWORD + "&newPassword=short&confirmPassword=short");
        assertThat(encoder.matches(PASSWORD, users.findByUsername("pw-changer").orElseThrow().getPasswordHash()))
                .isTrue();

        postForm(me, "/profile/password",
                "currentPassword=" + PASSWORD + "&newPassword=brand-new-long-pass&confirmPassword=brand-new-long-pass");

        assertThat(encoder.matches("brand-new-long-pass",
                users.findByUsername("pw-changer").orElseThrow().getPasswordHash())).isTrue();
        clientFor("pw-changer", "brand-new-long-pass").get().uri("/recipes").exchange().expectStatus().isOk();
    }

    @Test
    void usernameChangeWorksAndRefusesTakenNames() {
        guest("rename-me");
        guest("already-here");
        RestTestClient me = clientFor("rename-me", PASSWORD);

        postForm(me, "/profile/username", "username=ALREADY-HERE&currentPassword=" + PASSWORD);
        postForm(me, "/profile/username", "username=test&currentPassword=" + PASSWORD);
        postForm(me, "/profile/username", "username=renamed-person&currentPassword=wrong-password-here");
        assertThat(users.existsByUsername("rename-me")).isTrue();

        postForm(me, "/profile/username", "username=renamed-person&currentPassword=" + PASSWORD);

        assertThat(users.existsByUsername("rename-me")).isFalse();
        assertThat(users.existsByUsername("renamed-person")).isTrue();
        clientFor("renamed-person", PASSWORD).get().uri("/recipes").exchange().expectStatus().isOk();
    }

    @Test
    void emailAndNotificationChoicesAreSavedAndNeedAnEmail() {
        guest("notify-me");
        RestTestClient me = clientFor("notify-me", PASSWORD);

        postForm(me, "/profile/notifications", "newRecipes=true");
        assertThat(users.findByUsername("notify-me").orElseThrow().wantsNewRecipeEmails()).isFalse();

        postForm(me, "/profile/email", "email=not-an-email");
        assertThat(users.findByUsername("notify-me").orElseThrow().getEmail()).isNull();

        postForm(me, "/profile/email", "email=notify%40example.com");
        postForm(me, "/profile/notifications", "newRecipes=true&seasonChange=true");
        AppUser saved = users.findByUsername("notify-me").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("notify@example.com");
        assertThat(saved.wantsNewRecipeEmails()).isTrue();
        assertThat(saved.wantsSeasonChangeEmails()).isTrue();

        postForm(me, "/profile/notifications", "newRecipes=true");
        assertThat(users.findByUsername("notify-me").orElseThrow().wantsSeasonChangeEmails()).isFalse();

        postForm(me, "/profile/email", "email=");
        AppUser cleared = users.findByUsername("notify-me").orElseThrow();
        assertThat(cleared.getEmail()).isNull();
        assertThat(cleared.wantsNewRecipeEmails()).isFalse();
    }

    // ---- rules ----

    @Test
    void passwordRulesWantLongPasswordsThatAreNotEasyToGuess() {
        assertThat(accounts.passwordProblem("short", "someone")).isNotNull();
        assertThat(accounts.passwordProblem("aaaaaaaaaaaaaaaa", "someone")).isNotNull();
        assertThat(accounts.passwordProblem("password1234", "someone")).isNotNull();
        assertThat(accounts.passwordProblem("my-name-is-someone-ok", "someone")).isNotNull();
        assertThat(accounts.passwordProblem("x".repeat(80) + "yz", "someone")).isNotNull();
        assertThat(accounts.passwordProblem("blue-kettle-orbit-seven", "someone")).isNull();
    }

    @Test
    void usernameRulesRejectOddCharacters() {
        assertThat(accounts.usernameProblem("no<script>", null, false)).isNotNull();
        assertThat(accounts.usernameProblem("ab", null, false)).isNotNull();
        assertThat(accounts.usernameProblem("Nice Name_1", null, false)).isNull();
    }
}
