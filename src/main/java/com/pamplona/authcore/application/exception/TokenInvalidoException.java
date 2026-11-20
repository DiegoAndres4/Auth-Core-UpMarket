package com.pamplona.authcore.application.exception;

public class TokenInvalidoException extends ApiException {
    public TokenInvalidoException() {
        super("TOKEN_INVALIDO", 400, "El enlace de verificacion no es valido o ya fue utilizado");
    }
}
