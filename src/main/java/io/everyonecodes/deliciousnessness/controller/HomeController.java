package io.everyonecodes.deliciousnessness.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home(){
        return "redirect:/recipes";
    }


    // What anyone who is not logged in sees. It says they have no access and shows nothing else.
    @GetMapping("/private")
    public String privateSite(){
        return "private";
    }

    @GetMapping("/login")
    public String login(){
        return "login";
    }
}
