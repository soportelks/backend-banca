package com.lksbaas.mx.exception;

/**
 * Excepción lanzada cuando un código de seguridad ha alcanzado el límite de intentos fallidos permitidos.
 */
public class CodigoSeguridadBloqueadoException extends CodigoSeguridadException {

    public CodigoSeguridadBloqueadoException(String mensaje) {
        super(2, mensaje);
    }
}

