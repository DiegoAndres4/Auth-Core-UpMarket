package com.pamplona.authcore.support;

import com.pamplona.authcore.application.ports.out.EmailSenderPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adaptador de correo de prueba: captura los envios en memoria. Nunca toca red.
 */
public class CapturingEmailSender implements EmailSenderPort {

    public record SentEmail(String recipient, String link) {
    }

    private final List<SentEmail> sent = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendVerificationEmail(String recipient, String verificationLink) {
        sent.add(new SentEmail(recipient, verificationLink));
    }

    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public List<SentEmail> sentTo(String recipient) {
        return sent.stream().filter(email -> email.recipient().equals(recipient)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
