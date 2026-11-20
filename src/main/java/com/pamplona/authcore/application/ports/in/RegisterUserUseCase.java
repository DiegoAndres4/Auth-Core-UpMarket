package com.pamplona.authcore.application.ports.in;

import com.pamplona.authcore.domain.User;

/**
 * Puerto de entrada: registrar un nuevo usuario. El usuario queda sin correo
 * verificado y sin token de sesion hasta que confirme su correo.
 */
public interface RegisterUserUseCase {
    User register(String username, String rawPassword);
}
