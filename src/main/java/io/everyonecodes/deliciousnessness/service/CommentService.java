package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.controller.AccessAdvice;
import io.everyonecodes.deliciousnessness.dto.CommentDto;
import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Comment;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import io.everyonecodes.deliciousnessness.repository.CommentRepository;
import io.everyonecodes.deliciousnessness.repository.RecipeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CommentService {

    private static final int EXCERPT_LENGTH = 200;

    private final CommentRepository comments;
    private final RecipeRepository recipes;
    private final AppUserRepository users;
    private final ApplicationEventPublisher events;

    public CommentService(CommentRepository comments, RecipeRepository recipes, AppUserRepository users,
                          ApplicationEventPublisher events) {
        this.comments = comments;
        this.recipes = recipes;
        this.users = users;
        this.events = events;
    }

    // Top-level comments, oldest first, each with its replies. canDelete: you can remove your own
    // comments, and the owner can remove any.
    @Transactional(readOnly = true)
    public List<CommentDto> forRecipe(Long recipeId, String viewer, boolean viewerIsOwner) {
        List<Comment> topLevel = new ArrayList<>();
        Map<Long, List<CommentDto>> repliesByParent = new LinkedHashMap<>();
        for (Comment comment : comments.findForRecipe(recipeId)) {
            if (comment.getParent() == null) {
                topLevel.add(comment);
            } else {
                repliesByParent.computeIfAbsent(comment.getParent().getId(), key -> new ArrayList<>())
                        .add(toDto(comment, viewer, viewerIsOwner, List.of()));
            }
        }
        return topLevel.stream()
                .map(comment -> toDto(comment, viewer, viewerIsOwner,
                        repliesByParent.getOrDefault(comment.getId(), List.of())))
                .toList();
    }

    private static CommentDto toDto(Comment comment, String viewer, boolean viewerIsOwner, List<CommentDto> replies) {
        return new CommentDto(comment.getId(), comment.getAuthor().getUsername(), comment.getBody(),
                comment.getCreatedAt(), viewerIsOwner || comment.getAuthor().getUsername().equals(viewer), replies);
    }

    // repliedToId is the comment being answered, or null for a new top-level comment.
    // Null is returned when saved, otherwise what is wrong.
    @Transactional
    public String add(Long recipeId, String username, String text, Long repliedToId) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            return "Write something first.";
        }
        if (body.length() > Comment.MAX_LENGTH) {
            return "A comment can be at most " + Comment.MAX_LENGTH + " characters.";
        }
        AppUser author = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        var recipe = recipes.findById(recipeId)
                .filter(r -> !r.isPrivate() || AccessAdvice.ownerIsViewing())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));

        Comment repliedTo = null;
        Comment parent = null;
        if (repliedToId != null) {
            // Only a comment on this same recipe can be replied to.
            repliedTo = comments.findById(repliedToId)
                    .filter(comment -> comment.getRecipe().getId().equals(recipeId))
                    .orElse(null);
            if (repliedTo == null) {
                return "The comment you wanted to reply to is gone.";
            }
            // A reply to a reply stays under the same top-level comment.
            parent = repliedTo.getParent() != null ? repliedTo.getParent() : repliedTo;
        }

        comments.save(new Comment(recipe, author, body, parent));

        if (repliedTo != null && !repliedTo.getAuthor().getId().equals(author.getId())) {
            events.publishEvent(new CommentReplyEvent(recipeId, recipe.getRecipeName(),
                    repliedTo.getAuthor().getId(), author.getUsername(), excerpt(body)));
        }
        return null;
    }

    private static String excerpt(String body) {
        return body.length() <= EXCERPT_LENGTH ? body : body.substring(0, EXCERPT_LENGTH) + "...";
    }

    // Returns the recipe the comment was on, so the page can go back to it. Empty when it is not
    // there or when this person may not remove it. Its replies go with it.
    @Transactional
    public Optional<Long> delete(Long commentId, String username, boolean isOwner) {
        return comments.findById(commentId)
                .filter(c -> isOwner || c.getAuthor().getUsername().equals(username))
                .map(c -> {
                    Long recipeId = c.getRecipe().getId();
                    comments.delete(c);
                    return recipeId;
                });
    }
}
