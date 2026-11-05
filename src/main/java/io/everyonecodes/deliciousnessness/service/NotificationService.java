package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Season;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Emails the people who opted in on their profile: when a recipe is added, on the day the season pair
// changes (fall and winter, or spring and summer), and when someone replies to their comment.
// Each email is sent as plain text plus the styled HTML from templates/email.
@Service
public class NotificationService {

    private static final String PROFILE_NOTE = "\n\nYou get this because you turned it on in your profile. "
            + "You can turn it off there.";

    private final AppUserRepository users;
    private final MailService mail;
    private final EmailRenderer renderer;
    private final String publicUrl;

    public NotificationService(AppUserRepository users, MailService mail, EmailRenderer renderer,
                               @Value("${app.public-url:}") String publicUrl) {
        this.users = users;
        this.mail = mail;
        this.renderer = renderer;
        this.publicUrl = publicUrl.trim().replaceAll("/+$", "");
    }

    // Runs after the new recipe is saved, and off the request, so a slow mail server never slows a save.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRecipeCreated(RecipeCreatedEvent event) {
        if (!mail.isConfigured()) {
            return;
        }
        String link = link("/recipes/" + event.recipeId());
        String html = renderer.render("new-recipe", values("recipeName", event.recipeName(), "link", link));
        String text = "A new recipe was added: " + event.recipeName() + "." + linkLine(link) + PROFILE_NOTE;
        for (AppUser user : users.findByNotifyNewRecipesTrueAndEmailIsNotNull()) {
            mail.send(user.getEmail(), "New recipe: " + event.recipeName(), text, html);
        }
    }

    // Emails the author of a comment when someone replies to it, if they turned that on. Runs off the
    // request, after the reply is saved.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommentReply(CommentReplyEvent event) {
        if (!mail.isConfigured()) {
            return;
        }
        users.findById(event.recipientId())
                .filter(AppUser::wantsReplyEmails)
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .ifPresent(user -> {
                    String link = link("/recipes/" + event.recipeId() + "#comments");
                    String html = renderer.render("comment-reply", values("replierName", event.replierName(),
                            "recipeName", event.recipeName(), "excerpt", event.excerpt(), "link", link));
                    String text = event.replierName() + " replied to your comment on " + event.recipeName()
                            + ":\n\n" + event.excerpt() + linkLine(link) + PROFILE_NOTE;
                    mail.send(user.getEmail(),
                            event.replierName() + " replied to your comment on " + event.recipeName(), text, html);
                });
    }

    // Checked every morning. Sends only on the day the pair of seasons is different from yesterday's.
    @Scheduled(cron = "0 0 8 * * *")
    public void checkSeasonChange() {
        LocalDate today = LocalDate.now();
        List<Season> now = Season.inSeason(today);
        if (!now.equals(Season.inSeason(today.minusDays(1)))) {
            notifySeasonChange(now);
        }
    }

    public void notifySeasonChange(List<Season> seasons) {
        if (!mail.isConfigured()) {
            return;
        }
        String label = seasons.stream().map(Season::label).collect(Collectors.joining(" & "));
        String link = link("/recipes");
        String html = renderer.render("season-change", values("seasonLabel", label, "link", link));
        String text = "It's now " + label + " season. The front page shows the recipes that fit it."
                + linkLine(link) + PROFILE_NOTE;
        for (AppUser user : users.findByNotifySeasonChangeTrueAndEmailIsNotNull()) {
            mail.send(user.getEmail(), "The season changed: " + label, text, html);
        }
    }

    // Null when the public address is not set: the emails then have no links.
    private String link(String path) {
        return publicUrl.isEmpty() ? null : publicUrl + path;
    }

    private static String linkLine(String link) {
        return link == null ? "" : "\n\n" + link;
    }

    // What every template can use, plus the values for that one email.
    private Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        values.put("profileLink", link("/profile"));
        for (int i = 0; i < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1]);
        }
        return values;
    }
}
