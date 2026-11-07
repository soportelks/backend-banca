package com.lksbaas.mx.exception;

/**
 * Excepción lanzada cuando un código de seguridad ha sobrepasado su tiempo de expiración.
 */
public class CodigoSeguridadExpiradoException extends CodigoSeguridadException {

    public CodigoSeguridadExpiradoException(String mensaje) {
        super(3, mensaje);
    }
}

