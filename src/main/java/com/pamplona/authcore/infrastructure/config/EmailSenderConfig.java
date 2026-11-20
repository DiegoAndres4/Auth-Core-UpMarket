package com.pamplona.authcore.infrastructure.config;

import com.pamplona.authcore.application.ports.out.EmailSenderPort;
import com.pamplona.authcore.infrastructure.adapters.out.email.LoggingEmailSenderAdapter;
import com.pamplona.authcore.infrastructure.adapters.out.email.SmtpEmailSenderAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige el adaptador de correo: SMTP si MAIL_USERNAME esta configurado,
 * logging (solo desarrollo) en caso contrario.
 */
@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    public EmailSenderPort emailSenderPort(Environment environment,
                                           ObjectProvider<JavaMailSender> mailSenderProvider) {
        String username = environment.getProperty("spring.mail.username", "");
        if (username == null || username.isBlank()) {
            log.warn("Correo deshabilitado: MAIL_USERNAME no esta configurado; "
                    + "los enlaces de verificacion se registraran en el log (solo desarrollo).");
            return new LoggingEmailSenderAdapter();
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Correo deshabilitado: no hay JavaMailSender disponible; "
                    + "los enlaces de verificacion se registraran en el log (solo desarrollo).");
            return new LoggingEmailSenderAdapter();
        }
        String from = environment.getProperty("spring.mail.from", username);
        if (from == null || from.isBlank()) {
            from = username;
        }
        return new SmtpEmailSenderAdapter(mailSender, from);
    }
}
