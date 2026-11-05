package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByRoleOrRoleIsNull(Role role);

    List<AppUser> findByNotifyNewRecipesTrueAndEmailIsNotNull();

    List<AppUser> findByNotifySeasonChangeTrueAndEmailIsNotNull();
}
