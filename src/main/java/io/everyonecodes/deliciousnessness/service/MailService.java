package io.everyonecodes.deliciousnessness.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// Sends email only when a mail server is set up (SPRING_MAIL_HOST and APP_MAIL_FROM).
// Without it, nothing is sent and nothing breaks.
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final ClassPathResource LOGO = new ClassPathResource("static/icons/logo-email.png");

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.host = host.trim();
        this.from = from.trim();
    }

    public boolean isConfigured() {
        return !host.isEmpty() && !from.isEmpty() && sender.getIfAvailable() != null;
    }

    // Says in the log, once at startup, whether emails will be sent. The password is never logged.
    @EventListener(ApplicationReadyEvent.class)
    public void logStatus() {
        if (isConfigured()) {
            log.info("Email notifications are ON: sending through {} as {}", host, from);
        } else {
            log.info("Email notifications are OFF: set SPRING_MAIL_HOST and APP_MAIL_FROM to turn them on "
                    + "(host set: {}, from set: {})", !host.isEmpty(), !from.isEmpty());
        }
    }

    // One message with two versions: plain text for programs that cannot show HTML, and the styled HTML.
    // True when the mail server accepted it.
    public boolean send(String to, String subject, String plainText, String html) {
        if (!isConfigured()) {
            log.info("Email not sent: email notifications are OFF");
            return false;
        }
        try {
            JavaMailSender mailSender = sender.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            // A line break in a subject could add extra mail headers.
            helper.setSubject(subject.replaceAll("[\\r\\n]+", " "));
            helper.setText(plainText, html);
            // The logo travels inside the email (the HTML shows it as cid:logo), so email programs show it
            // without loading anything from the site.
            helper.addInline("logo", LOGO, "image/png");
            mailSender.send(message);
            log.info("Email sent");
            return true;
        } catch (MailException | MessagingException e) {
            log.warn("Could not send an email notification: {}", e.getMessage());
            return false;
        }
    }
}
