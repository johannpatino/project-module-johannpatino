package io.everyonecodes.deliciousnessness.security;

import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Role;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserSeeder implements CommandLineRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public UserSeeder(AppUserRepository users, PasswordEncoder encoder,
                      @Value("${app.seed.username:}") String username,
                      @Value("${app.seed.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        // Only seeds when there is no owner yet. If the owner renames their account in the profile,
        // the seed name must not come back as a second owner with the seed password.
        if (username.isBlank() || users.existsByRoleOrRoleIsNull(Role.USER) || users.existsByUsername(username)) {
            return;
        }
        users.save(new AppUser(username, encoder.encode(password)));
    }
}