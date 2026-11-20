package com.pamplona.authcore.application.ports.in;

/**
 * Puerto de entrada: confirmar el correo de un usuario con el token recibido.
 */
public interface VerifyEmailUseCase {
    void verifyEmail(String rawToken);
}
