package com.pamplona.authcore.application.exception;

/**
 * Falla al entregar un correo. La aplicacion la captura para no romper el
 * registro: el usuario queda creado sin verificar y puede usar el reenvio.
 */
public class EmailSendException extends RuntimeException {
    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
