package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.model.AppUser;
import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import io.everyonecodes.deliciousnessness.security.AttemptLimiter;
import io.everyonecodes.deliciousnessness.service.AccountService;
import io.everyonecodes.deliciousnessness.service.InviteService;
import io.everyonecodes.deliciousnessness.service.MailService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

// Everyone, guests and the owner, can change their own details here. Nobody can touch anyone else's.
@Controller
@RequestMapping("/profile")
public class ProfileController {

    // Changing the password or username asks for the current password. This stops someone at an
    // unlocked computer from guessing it there.
    private final AttemptLimiter passwordChecks = new AttemptLimiter(5, Duration.ofMinutes(10));

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AccountService accounts;
    private final MailService mail;
    private final InviteService inviteService;

    public ProfileController(AppUserRepository users, PasswordEncoder encoder, AccountService accounts,
                             MailService mail, InviteService inviteService) {
        this.inviteService = inviteService;
        this.users = users;
        this.encoder = encoder;
        this.accounts = accounts;
        this.mail = mail;
    }

    @GetMapping
    public String show(Authentication authentication, Model model) {
        AppUser user = current(authentication);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("newRecipes", user.wantsNewRecipeEmails());
        model.addAttribute("seasonChange", user.wantsSeasonChangeEmails());
        model.addAttribute("replies", user.wantsReplyEmails());
        model.addAttribute("mailReady", mail.isConfigured());
        model.addAttribute("minPassword", AccountService.MIN_PASSWORD);
        // Only the owner manages invites. Guests never get the list.
        if (AccessAdvice.isOwner(authentication)) {
            model.addAttribute("invites", inviteService.rows());
        }
        return "profile";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String confirmPassword,
                                 Authentication authentication, HttpServletRequest request,
                                 HttpServletResponse response, RedirectAttributes redirect) {
        AppUser user = current(authentication);
        String problem = wrongCurrentPassword(user, currentPassword);
        if (problem == null && !newPassword.equals(confirmPassword)) {
            problem = "The two new passwords don't match.";
        }
        if (problem == null && newPassword.equals(currentPassword)) {
            problem = "Pick a password you haven't used here before.";
        }
        if (problem == null) {
            problem = accounts.passwordProblem(newPassword, user.getUsername());
        }
        if (problem != null) {
            return back(redirect, problem, null);
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        return signOut(authentication, request, response);
    }

    @PostMapping("/username")
    public String changeUsername(@RequestParam(defaultValue = "") String username,
                                 @RequestParam(defaultValue = "") String currentPassword,
                                 Authentication authentication, HttpServletRequest request,
                                 HttpServletResponse response, RedirectAttributes redirect) {
        AppUser user = current(authentication);
        String name = username.trim();
        String problem = wrongCurrentPassword(user, currentPassword);
        if (problem == null && name.equals(user.getUsername())) {
            problem = "That is already your username.";
        }
        if (problem == null) {
            problem = accounts.usernameProblem(name, user.getId(), AccessAdvice.isOwner(authentication));
        }
        if (problem != null) {
            return back(redirect, problem, null);
        }
        user.setUsername(name);
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) {
            return back(redirect, AccountService.TAKEN, null);
        }
        return signOut(authentication, request, response);
    }

    @PostMapping("/email")
    public String changeEmail(@RequestParam(defaultValue = "") String email, Authentication authentication,
                              RedirectAttributes redirect) {
        AppUser user = current(authentication);
        String address = email.trim();
        String problem = accounts.emailProblem(address);
        if (problem != null) {
            return back(redirect, problem, null);
        }
        user.setEmail(address.isEmpty() ? null : address);
        if (address.isEmpty()) {
            // No address to send to, so notifications go off with it.
            user.setNotifyNewRecipes(false);
            user.setNotifySeasonChange(false);
            user.setNotifyReplies(false);
        }
        users.save(user);
        return back(redirect, null, address.isEmpty() ? "Email removed." : "Email saved.");
    }

    @PostMapping("/notifications")
    public String changeNotifications(@RequestParam(defaultValue = "false") boolean newRecipes,
                                      @RequestParam(defaultValue = "false") boolean seasonChange,
                                      @RequestParam(defaultValue = "false") boolean replies,
                                      Authentication authentication, RedirectAttributes redirect) {
        AppUser user = current(authentication);
        if ((newRecipes || seasonChange || replies) && (user.getEmail() == null || user.getEmail().isBlank())) {
            return back(redirect, "Add an email address first, then turn notifications on.", null);
        }
        user.setNotifyNewRecipes(newRecipes);
        user.setNotifySeasonChange(seasonChange);
        user.setNotifyReplies(replies);
        users.save(user);
        return back(redirect, null, "Notification choices saved.");
    }

    private AppUser current(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    // Null when the typed password is right.
    private String wrongCurrentPassword(AppUser user, String typed) {
        if (passwordChecks.isLimited(user.getUsername())) {
            return "Too many wrong tries. Wait a few minutes.";
        }
        if (!encoder.matches(typed, user.getPasswordHash())) {
            passwordChecks.record(user.getUsername());
            return "Your current password is wrong.";
        }
        passwordChecks.clear(user.getUsername());
        return null;
    }

    private String back(RedirectAttributes redirect, String problem, String message) {
        if (problem != null) {
            redirect.addFlashAttribute("problem", problem);
        }
        if (message != null) {
            redirect.addFlashAttribute("message", message);
        }
        return "redirect:/profile";
    }

    // A new password or username ends this login, and the "stay logged in" cookie with it.
    // The person logs in again with the new details.
    private String signOut(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        Cookie remember = new Cookie("remember-me", null);
        remember.setMaxAge(0);
        remember.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        remember.setHttpOnly(true);
        response.addCookie(remember);
        return "redirect:/login?changed";
    }
}
