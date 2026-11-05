package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.service.AccountService;
import io.everyonecodes.deliciousnessness.service.InviteService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


// There is no open sign-up. A guest account can only be made with an invite link from the owner,
// and each link works once. Guests can look at recipes, search and filter, and nothing else.
@Controller
public class SignupController {

    private final InviteService inviteService;
    private final AccountService accounts;
    private final PasswordEncoder encoder;

    public SignupController(InviteService inviteService, AccountService accounts, PasswordEncoder encoder) {
        this.inviteService = inviteService;
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @GetMapping("/signup/{token}")
    public String form(@PathVariable String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("linkWorks", inviteService.isUsable(token));
        model.addAttribute("minPassword", AccountService.MIN_PASSWORD);
        return "signup";
    }

    @PostMapping("/signup/{token}")
    public String signup(@PathVariable String token, @RequestParam String username,
                         @RequestParam String password, Model model) {
        String name = username.trim();
        model.addAttribute("token", token);
        model.addAttribute("linkWorks", true);
        model.addAttribute("username", name);
        model.addAttribute("minPassword", AccountService.MIN_PASSWORD);

        String problem = accounts.usernameProblem(name, null, false);
        if (problem == null) {
            problem = accounts.passwordProblem(password, name);
        }
        if (problem != null) {
            model.addAttribute("problem", problem);
            return "signup";
        }
        try {
            if (!inviteService.redeem(token, name, encoder.encode(password))) {
                model.addAttribute("linkWorks", false);
                return "signup";
            }
        } catch (DataIntegrityViolationException e) {
            // Someone took the name a moment ago. The invite was not used up.
            model.addAttribute("problem", AccountService.TAKEN);
            return "signup";
        }
        return "redirect:/login?registered";
    }
}
