package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    Optional<Invite> findByTokenHash(String tokenHash);

    List<Invite> findAllByOrderByCreatedAtDesc();

    // Marks the invite as used only if it is still unused and not expired. One row changed means this
    // caller got it, so two people opening the same link at once cannot both sign up.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Invite i SET i.usedAt = :now, i.usedBy = :username
            WHERE i.tokenHash = :tokenHash AND i.usedAt IS NULL AND i.expiresAt > :now
            """)
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now,
                 @Param("username") String username);
}
