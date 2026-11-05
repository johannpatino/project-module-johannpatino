package io.everyonecodes.deliciousnessness.dto;

import java.time.LocalDateTime;

// One line of the invite list on the profile page. status is "waiting", "used" or "expired".
public record InviteRow(Long id, LocalDateTime createdAt, LocalDateTime expiresAt, String status, String usedBy) {
}
