package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.InviteRow;
import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Invite;
import io.everyonecodes.deliciousnessness.model.Role;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import io.everyonecodes.deliciousnessness.repository.InviteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class InviteService {

    private static final Duration VALID_FOR = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final InviteRepository invites;
    private final AppUserRepository users;
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteRepository invites, AppUserRepository users) {
        this.invites = invites;
        this.users = users;
    }

    // Returns the token. It is shown once and never stored, only its hash.
    public String create() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        invites.save(new Invite(hash(token), now, now.plus(VALID_FOR)));
        return token;
    }

    public boolean isUsable(String token) {
        return invites.findByTokenHash(hash(token))
                .filter(invite -> invite.getUsedAt() == null
                        && invite.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    // Uses up the invite and makes the guest in one step. If making the guest fails, for example
    // because the username was just taken, the invite is not used up.
    @Transactional
    public boolean redeem(String token, String username, String passwordHash) {
        if (invites.markUsed(hash(token), LocalDateTime.now(), username) != 1) {
            return false;
        }
        users.save(new AppUser(username, passwordHash, Role.GUEST));
        return true;
    }

    public List<InviteRow> rows() {
        LocalDateTime now = LocalDateTime.now();
        return invites.findAllByOrderByCreatedAtDesc().stream()
                .map(invite -> new InviteRow(invite.getId(), invite.getCreatedAt(), invite.getExpiresAt(),
                        statusOf(invite, now), invite.getUsedBy()))
                .toList();
    }

    private static String statusOf(Invite invite, LocalDateTime now) {
        if (invite.getUsedAt() != null) {
            return "used";
        }
        return invite.getExpiresAt().isAfter(now) ? "waiting" : "expired";
    }

    public void delete(Long id) {
        invites.deleteById(id);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
