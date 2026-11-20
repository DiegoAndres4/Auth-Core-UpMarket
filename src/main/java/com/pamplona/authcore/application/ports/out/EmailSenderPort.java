package com.pamplona.authcore.application.ports.out;

/**
 * Puerto de salida: envio del correo de verificacion. La capa de aplicacion
 * no conoce Spring Mail ni Jakarta Mail; solo entrega el destinatario y el
 * enlace de verificacion.
 */
public interface EmailSenderPort {
    void sendVerificationEmail(String recipient, String verificationLink);
}
