package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.service.InviteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Only the owner reaches these routes (every route outside the guest list needs the USER role).
// The invite list itself is on the profile page.
@Controller
@RequestMapping("/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    // The invites used to have a page of their own. Old bookmarks go to the profile.
    @GetMapping
    public String list() {
        return "redirect:/profile";
    }

    @PostMapping
    public String create(HttpServletRequest request, RedirectAttributes redirect) {
        String token = inviteService.create();
        // The link is shown once, on the page we go back to. Only its hash is kept.
        redirect.addFlashAttribute("newLink", ServletUriComponentsBuilder.fromContextPath(request)
                .path("/signup/{token}").buildAndExpand(token).toUriString());
        return "redirect:/profile#invites";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        inviteService.delete(id);
        return "redirect:/profile#invites";
    }
}
