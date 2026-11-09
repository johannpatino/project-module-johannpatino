package io.everyonecodes.deliciousnessness.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MailServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void everyEmailCarriesTheLogoInside() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);

        assertTrue(new MailService(provider, "smtp.example.test", "from@example.test")
                .send("to@example.test", "Hi", "text", "<img src=\"cid:logo\">"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent.getValue().writeTo(raw);
        String message = raw.toString();
        assertTrue(message.contains("Content-ID: <logo>"));
        assertTrue(message.contains("image/png"));
    }
}
