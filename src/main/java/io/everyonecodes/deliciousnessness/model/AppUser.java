package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 72)
    private String passwordHash;

    // Accounts made before roles existed have no role stored. They are the full-access user.
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Role role;

    public AppUser(String username, String passwordHash) {
        this(username, passwordHash, Role.USER);
    }

    public AppUser(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    @Column(length = 254)
    private String email;

    // Nullable on purpose: accounts made before these existed have no value, which means "off".
    private Boolean notifyNewRecipes;

    private Boolean notifySeasonChange;

    private Boolean notifyReplies;

    public boolean wantsNewRecipeEmails() {
        return Boolean.TRUE.equals(notifyNewRecipes);
    }

    public boolean wantsSeasonChangeEmails() {
        return Boolean.TRUE.equals(notifySeasonChange);
    }

    public boolean wantsReplyEmails() {
        return Boolean.TRUE.equals(notifyReplies);
    }

    public Role effectiveRole() {
        return role == null ? Role.USER : role;
    }
}
