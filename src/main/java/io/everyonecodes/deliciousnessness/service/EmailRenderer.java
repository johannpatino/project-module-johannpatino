package io.everyonecodes.deliciousnessness.service;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

// Turns the email templates (templates/email/*.html) into the HTML of a message. Everything put into a
// template is escaped, so a recipe name or a comment can never add markup to an email.
@Component
public class EmailRenderer {

    private final SpringTemplateEngine engine;

    public EmailRenderer(SpringTemplateEngine engine) {
        this.engine = engine;
    }

    public String render(String template, Map<String, Object> values) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(values);
        return engine.process("email/" + template, context);
    }
}
