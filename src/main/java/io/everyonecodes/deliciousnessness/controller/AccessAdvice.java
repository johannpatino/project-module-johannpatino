package io.everyonecodes.deliciousnessness.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Templates use "canEdit" to hide the planner and the add, edit and delete buttons from guests.
@ControllerAdvice
public class AccessAdvice {

    // The owner is the one account with the USER role. Guests have GUEST.
    public static boolean isOwner(Authentication authentication) {
        return authentication != null
                && AuthorityUtils.authorityListToSet(authentication.getAuthorities()).contains("ROLE_USER");
    }

    @ModelAttribute("canEdit")
    public boolean canEdit(Authentication authentication) {
        return isOwner(authentication);
    }
}
