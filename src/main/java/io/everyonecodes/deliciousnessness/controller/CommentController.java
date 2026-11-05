package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.security.AttemptLimiter;
import io.everyonecodes.deliciousnessness.service.CommentService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

// Guests and the owner can all comment. Which routes they may reach is set in SecurityConfig.
@Controller
public class CommentController {

    // Keeps one account from flooding a recipe.
    private final AttemptLimiter posts = new AttemptLimiter(10, Duration.ofMinutes(10));

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/recipes/{id}/comments")
    public String add(@PathVariable Long id, @RequestParam String body,
                      @RequestParam(required = false) Long parent,
                      Authentication authentication, RedirectAttributes redirect) {
        String problem;
        if (posts.isLimited(authentication.getName())) {
            problem = "You're commenting too fast. Wait a few minutes.";
        } else {
            posts.record(authentication.getName());
            problem = commentService.add(id, authentication.getName(), body, parent);
        }
        if (problem != null) {
            redirect.addFlashAttribute("commentProblem", problem);
            redirect.addFlashAttribute("commentDraft", body);
            // Which reply box the problem belongs to. Empty for the main comment box.
            redirect.addFlashAttribute("replyToId", parent);
        }
        return "redirect:/recipes/" + id + "#comments";
    }

    @PostMapping("/comments/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        return commentService.delete(id, authentication.getName(), AccessAdvice.isOwner(authentication))
                .map(recipeId -> "redirect:/recipes/" + recipeId + "#comments")
                .orElse("redirect:/recipes");
    }
}
