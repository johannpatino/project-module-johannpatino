package io.everyonecodes.deliciousnessness.dto;

import java.time.LocalDateTime;
import java.util.List;

// replies is empty on a reply: they only go one level deep.
public record CommentDto(Long id, String authorName, String body, LocalDateTime createdAt, boolean canDelete,
                         List<CommentDto> replies) {
}
