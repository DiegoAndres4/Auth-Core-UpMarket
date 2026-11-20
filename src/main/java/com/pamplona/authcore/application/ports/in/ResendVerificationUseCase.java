package com.pamplona.authcore.application.ports.in;

/**
 * Puerto de entrada: reenviar el correo de verificacion. Nunca revela si la
 * cuenta existe (el adaptador responde siempre 202).
 */
public interface ResendVerificationUseCase {
    void resendVerification(String username);
}
