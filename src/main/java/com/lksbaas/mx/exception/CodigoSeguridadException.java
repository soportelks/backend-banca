package com.lksbaas.mx.exception;

/**
 * Excepción base para errores relacionados con el mecanismo de códigos de seguridad.
 */
public class CodigoSeguridadException extends RuntimeException {

    private final int codigoError;

    public CodigoSeguridadException(String mensaje) {
        super(mensaje);
        this.codigoError = 1;
    }

    public CodigoSeguridadException(int codigoError, String mensaje) {
        super(mensaje);
        this.codigoError = codigoError;
    }

    public CodigoSeguridadException(String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigoError = 1;
    }

    public int getCodigoError() {
        return codigoError;
    }
}

