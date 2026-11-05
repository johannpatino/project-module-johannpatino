package io.everyonecodes.deliciousnessness.service;

// Someone replied to a comment. recipientId is the author of the comment that was replied to.
public record CommentReplyEvent(Long recipeId, String recipeName, Long recipientId, String replierName,
                                String excerpt) {
}
