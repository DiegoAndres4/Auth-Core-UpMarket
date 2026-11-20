package com.pamplona.authcore.infrastructure.adapters.out.email;

import com.pamplona.authcore.application.exception.EmailSendException;
import com.pamplona.authcore.application.ports.out.EmailSenderPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

/**
 * Adaptador SMTP (Gmail u otro) del correo de verificacion. Solo se usa cuando
 * MAIL_USERNAME tiene valor. Las credenciales jamas se registran en el log.
 */
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSenderAdapter(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationEmail(String recipient, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(VerificationEmailContent.SUBJECT);
            helper.setText(
                    VerificationEmailContent.plainText(verificationLink),
                    VerificationEmailContent.html(verificationLink));
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new EmailSendException("No se pudo enviar el correo de verificacion", ex);
        }
    }
}
