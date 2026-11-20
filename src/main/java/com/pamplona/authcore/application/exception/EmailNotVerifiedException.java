package com.pamplona.authcore.application.exception;

public class EmailNotVerifiedException extends ApiException {
    public EmailNotVerifiedException() {
        super("EMAIL_NO_VERIFICADO", 403, "Debes confirmar tu correo antes de iniciar sesion");
    }
}
