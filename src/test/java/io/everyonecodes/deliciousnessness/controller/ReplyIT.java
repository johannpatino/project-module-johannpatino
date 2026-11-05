package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.dto.CommentDto;
import io.everyonecodes.deliciousnessness.dto.CreateRecipeRequest;
import io.everyonecodes.deliciousnessness.dto.RecipeDto;
import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Comment;
import io.everyonecodes.deliciousnessness.model.Role;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import io.everyonecodes.deliciousnessness.repository.CommentRepository;
import io.everyonecodes.deliciousnessness.service.CommentReplyEvent;
import io.everyonecodes.deliciousnessness.service.CommentService;
import io.everyonecodes.deliciousnessness.service.EmailRenderer;
import io.everyonecodes.deliciousnessness.service.MailService;
import io.everyonecodes.deliciousnessness.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEvent;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@RecordApplicationEvents
class ReplyIT {

    private static final String PASSWORD = "a-long-test-password";

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private CommentRepository comments;

    @Autowired
    private CommentService commentService;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EmailRenderer renderer;

    private RestTestClient owner;

    @BeforeEach
    void setUp() {
        owner = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("test", "test"))
                .build();
    }

    private AppUser guest(String name) {
        return users.save(new AppUser(name, encoder.encode(PASSWORD), Role.GUEST));
    }

    private Long recipe() {
        CreateRecipeRequest request = new CreateRecipeRequest("Reply target", 1, 1, "x", Set.of(),
                null, null, null, Set.of(), List.of());
        return owner.post().uri("/api/recipes").contentType(MediaType.APPLICATION_JSON).body(request)
                .exchange().expectStatus().isCreated()
                .expectBody(RecipeDto.class).returnResult().getResponseBody().id();
    }

    private Long topLevelId(Long recipeId) {
        return comments.findForRecipe(recipeId).stream()
                .filter(c -> c.getParent() == null).findFirst().orElseThrow().getId();
    }

    private List<CommentReplyEvent> replyEvents() {
        return events.stream(CommentReplyEvent.class).toList();
    }

    @Test
    void replyHangsUnderTheCommentAndShowsOnThePage() {
        guest("reply-a");
        guest("reply-b");
        Long recipeId = recipe();

        assertThat(commentService.add(recipeId, "reply-a", "Great with basil", null)).isNull();
        assertThat(commentService.add(recipeId, "reply-b", "I agree, it is great", topLevelId(recipeId))).isNull();

        List<CommentDto> tree = commentService.forRecipe(recipeId, "reply-a", false);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).replies()).hasSize(1);
        assertThat(tree.get(0).replies().get(0).authorName()).isEqualTo("reply-b");

        owner.get().uri("/recipes/" + recipeId).exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("Great with basil").contains("I agree, it is great")
                        .contains("Post reply").contains("Comments (2)"));
    }

    @Test
    void replyToAReplyStaysUnderTheSameTopLevelComment() {
        guest("nest-a");
        guest("nest-b");
        guest("nest-c");
        Long recipeId = recipe();
        commentService.add(recipeId, "nest-a", "top", null);
        Long top = topLevelId(recipeId);
        commentService.add(recipeId, "nest-b", "first reply", top);
        Long firstReply = comments.findForRecipe(recipeId).stream()
                .filter(c -> c.getParent() != null).findFirst().orElseThrow().getId();

        commentService.add(recipeId, "nest-c", "reply to the reply", firstReply);

        List<Comment> all = comments.findForRecipe(recipeId);
        assertThat(all).hasSize(3);
        assertThat(all.stream().filter(c -> c.getParent() != null))
                .allSatisfy(c -> assertThat(c.getParent().getId()).isEqualTo(top));
        // The person who wrote the reply that was answered is the one who gets told.
        assertThat(replyEvents().get(replyEvents().size() - 1).recipientId())
                .isEqualTo(users.findByUsername("nest-b").orElseThrow().getId());
    }

    @Test
    void cannotReplyToACommentOnAnotherRecipe() {
        guest("cross-a");
        Long first = recipe();
        Long second = recipe();
        commentService.add(first, "cross-a", "on the first", null);

        String problem = commentService.add(second, "cross-a", "sneaky", topLevelId(first));

        assertThat(problem).isNotNull();
        assertThat(comments.findForRecipe(second)).isEmpty();
    }

    @Test
    void deletingACommentDeletesItsReplies() {
        guest("del-a");
        guest("del-b");
        Long recipeId = recipe();
        commentService.add(recipeId, "del-a", "parent", null);
        commentService.add(recipeId, "del-b", "child", topLevelId(recipeId));
        assertThat(comments.findForRecipe(recipeId)).hasSize(2);

        commentService.delete(topLevelId(recipeId), "del-a", false);

        assertThat(comments.findForRecipe(recipeId)).isEmpty();
    }

    @Test
    void aReplyTellsTheOtherPersonButNotYourself() {
        guest("tell-a");
        guest("tell-b");
        Long recipeId = recipe();
        commentService.add(recipeId, "tell-a", "mine", null);
        int before = replyEvents().size();

        commentService.add(recipeId, "tell-a", "replying to myself", topLevelId(recipeId));
        assertThat(replyEvents()).hasSize(before);

        commentService.add(recipeId, "tell-b", "replying to a", topLevelId(recipeId));
        assertThat(replyEvents()).hasSize(before + 1);
        CommentReplyEvent event = replyEvents().get(before);
        assertThat(event.recipientId()).isEqualTo(users.findByUsername("tell-a").orElseThrow().getId());
        assertThat(event.replierName()).isEqualTo("tell-b");
        assertThat(event.recipeId()).isEqualTo(recipeId);
    }

    @Test
    void replyEmailGoesOnlyToThoseWhoOptedInAndHaveAnAddress() {
        AppUser wants = guest("mail-wants");
        wants.setEmail("wants@example.com");
        wants.setNotifyReplies(true);
        users.save(wants);
        AppUser off = guest("mail-off");
        off.setEmail("off@example.com");
        users.save(off);
        AppUser noAddress = guest("mail-noaddress");
        noAddress.setNotifyReplies(true);
        users.save(noAddress);

        MailService mail = mock(MailService.class);
        when(mail.isConfigured()).thenReturn(true);
        NotificationService notifications = new NotificationService(users, mail, renderer, "https://example.test/");

        notifications.onCommentReply(new CommentReplyEvent(7L, "Soup", off.getId(), "someone", "hi"));
        notifications.onCommentReply(new CommentReplyEvent(7L, "Soup", noAddress.getId(), "someone", "hi"));
        verify(mail, never()).send(anyString(), anyString(), anyString(), anyString());

        notifications.onCommentReply(new CommentReplyEvent(7L, "Soup", wants.getId(), "someone", "nice one"));
        verify(mail).send(eq("wants@example.com"), contains("someone replied to your comment on Soup"),
                contains("https://example.test/recipes/7#comments"), contains("<html"));
    }

    @Test
    void noReplyEmailWhenMailIsNotSwitchedOn() {
        AppUser wants = guest("mail-nomailserver");
        wants.setEmail("x@example.com");
        wants.setNotifyReplies(true);
        users.save(wants);
        MailService mail = mock(MailService.class);
        when(mail.isConfigured()).thenReturn(false);

        new NotificationService(users, mail, renderer, "").onCommentReply(
                new CommentReplyEvent(1L, "Soup", wants.getId(), "someone", "hi"));

        verify(mail, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void emailTemplatesShowTheTextButNeverRunItAsHtml() {
        String hostile = "<script>alert(1)</script> <b>bold</b>";

        String reply = renderer.render("comment-reply", java.util.Map.of("replierName", hostile,
                "recipeName", hostile, "excerpt", hostile, "link", "https://example.test/recipes/1#comments",
                "profileLink", "https://example.test/profile"));
        String recipe = renderer.render("new-recipe", java.util.Map.of("recipeName", hostile));
        String season = renderer.render("season-change", java.util.Map.of("seasonLabel", "Fall & Winter"));

        for (String html : List.of(reply, recipe, season)) {
            assertThat(html).doesNotContain("<script>").doesNotContain("<b>bold</b>").contains("Deliciousnessness");
        }
        assertThat(reply).contains("&lt;script&gt;").contains("Read and reply")
                .contains("https://example.test/recipes/1#comments").contains("https://example.test/profile");
        assertThat(recipe).doesNotContain("View the recipe");
        assertThat(season).contains("Fall &amp; Winter");
    }

    @Test
    void replyOptionIsSavedOnTheProfileAndNeedsAnEmail() {
        guest("pref-replies");
        RestTestClient me = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("pref-replies", PASSWORD))
                .build();

        me.post().uri("/profile/notifications").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("replies=true").exchange().expectStatus().is3xxRedirection();
        assertThat(users.findByUsername("pref-replies").orElseThrow().wantsReplyEmails()).isFalse();

        me.post().uri("/profile/email").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("email=pref%40example.com").exchange().expectStatus().is3xxRedirection();
        me.post().uri("/profile/notifications").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("replies=true").exchange().expectStatus().is3xxRedirection();
        assertThat(users.findByUsername("pref-replies").orElseThrow().wantsReplyEmails()).isTrue();

        me.get().uri("/profile").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("Email me when someone replies to my comment"));
    }
}
