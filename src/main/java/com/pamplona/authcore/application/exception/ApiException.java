package com.pamplona.authcore.application.exception;

/**
 * Excepcion de aplicacion con un codigo estable y un estado HTTP asociado.
 * No depende de Spring: el adaptador REST traduce el codigo y el estado.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    protected ApiException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
