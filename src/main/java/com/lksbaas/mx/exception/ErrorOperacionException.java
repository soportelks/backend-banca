package com.lksbaas.mx.exception;

/**
 * Error no controlado durante una operación interna del backend.
 */
public class ErrorOperacionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ErrorOperacionException(String mensaje) {
        super(mensaje);
    }

    public ErrorOperacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
