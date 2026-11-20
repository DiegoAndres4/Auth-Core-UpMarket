package com.pamplona.authcore.infrastructure.adapters.out.email;

import com.pamplona.authcore.application.ports.out.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptador de desarrollo: sin MAIL_USERNAME no se envia nada, solo se
 * registra el enlace. Nunca se usa en produccion.
 */
public class LoggingEmailSenderAdapter implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSenderAdapter.class);

    @Override
    public void sendVerificationEmail(String recipient, String verificationLink) {
        log.warn("Correo deshabilitado: enlace de verificacion para {}: {}", recipient, verificationLink);
    }
}
