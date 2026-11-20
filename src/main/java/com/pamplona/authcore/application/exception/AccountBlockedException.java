package com.pamplona.authcore.application.exception;

public class AccountBlockedException extends ApiException {
    public AccountBlockedException() {
        super("CUENTA_BLOQUEADA", 403, "Tu cuenta ha sido bloqueada");
    }
}
