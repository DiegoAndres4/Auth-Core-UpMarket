package com.pamplona.authcore.application.exception;

public class CannotBlockSelfException extends ApiException {
    public CannotBlockSelfException() {
        super("NO_PUEDES_BLOQUEARTE", 409, "No puedes bloquear tu propia cuenta");
    }
}
