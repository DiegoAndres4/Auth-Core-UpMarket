package com.pamplona.authcore.application.exception;

public class CannotBlockAdminException extends ApiException {
    public CannotBlockAdminException() {
        super("NO_SE_BLOQUEA_ADMIN", 409, "No se puede bloquear a un administrador");
    }
}
