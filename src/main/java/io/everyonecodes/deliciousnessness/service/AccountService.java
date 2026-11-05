package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// The rules for usernames, passwords and email addresses. Sign-up and the profile page both use them.
@Service
public class AccountService {

    public static final String TAKEN = "That username is taken.";

    private static final int MIN_USERNAME = 3;
    private static final int MAX_USERNAME = 64;
    // Long passwords are what protects an account that is reachable from the internet.
    // BCrypt only reads the first 72 bytes, so that is the longest password that counts.
    public static final int MIN_PASSWORD = 12;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_EMAIL = 254;

    // Letters, digits, spaces and . _ - only: no control characters, no look-alike tricks with markup.
    private static final Pattern USERNAME_CHARS = Pattern.compile("^[\\p{L}\\p{N} ._-]+$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1234", "passwordpassword", "123456789012", "1234567890123",
            "qwertyuiopas", "qwertyqwerty", "iloveyou1234", "letmeinletmein",
            "welcome12345", "administrator");

    private final AppUserRepository users;
    // The owner's username can never be taken by a guest, even before the owner's account exists.
    private final String reservedUsername;

    public AccountService(AppUserRepository users, @Value("${app.seed.username:}") String reservedUsername) {
        this.users = users;
        this.reservedUsername = reservedUsername.trim();
    }

    // ownId is the account being renamed, or null for a new account. Null when the name is fine.
    public String usernameProblem(String name, Long ownId, boolean mayUseReservedName) {
        if (name.length() < MIN_USERNAME || name.length() > MAX_USERNAME) {
            return "Username must be " + MIN_USERNAME + " to " + MAX_USERNAME + " characters.";
        }
        if (!USERNAME_CHARS.matcher(name).matches()) {
            return "Username can only use letters, numbers, spaces, dots, dashes and underscores.";
        }
        if (!mayUseReservedName && name.equalsIgnoreCase(reservedUsername)) {
            return TAKEN;
        }
        boolean taken = ownId == null
                ? users.existsByUsernameIgnoreCase(name)
                : users.existsByUsernameIgnoreCaseAndIdNot(name, ownId);
        return taken ? TAKEN : null;
    }

    public String passwordProblem(String password, String username) {
        if (password.length() < MIN_PASSWORD) {
            return "Password must be at least " + MIN_PASSWORD + " characters. A few random words work well.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            return "Password is too long.";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower) || password.chars().distinct().count() < 4) {
            return "That password is too easy to guess.";
        }
        if (username != null && username.length() >= MIN_USERNAME && lower.contains(username.toLowerCase(Locale.ROOT))) {
            return "Password must not contain your username.";
        }
        return null;
    }

    // Blank means "no email". Null when the address is fine.
    public String emailProblem(String email) {
        if (email.isEmpty()) {
            return null;
        }
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            return "That doesn't look like an email address.";
        }
        return null;
    }
}
