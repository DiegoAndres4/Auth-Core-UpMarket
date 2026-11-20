package com.pamplona.authcore.application.exception;

public class TokenExpiradoException extends ApiException {
    public TokenExpiradoException() {
        super("TOKEN_EXPIRADO", 400, "El enlace de verificacion ha expirado; solicita uno nuevo");
    }
}
