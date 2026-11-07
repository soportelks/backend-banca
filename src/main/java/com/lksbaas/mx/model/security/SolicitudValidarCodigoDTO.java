package com.lksbaas.mx.model.security;

/**
 * DTO para la solicitud de validación de un código de seguridad para una transacción.
 */
public class SolicitudValidarCodigoDTO {

    /**
     * Tipo de transacción u operación para la cual se valida el código.
     */
    private String tipoTransaccion;

    /**
     * Código de seguridad introducido por el usuario (e.g. "123456").
     */
    private String codigoSeguridad;

    /**
     * Token de solicitud asociado a la emisión (opcional pero recomendado para vincular a la sesión de envío).
     */
    private String tokenSolicitud;

    public SolicitudValidarCodigoDTO() {
    }

    public SolicitudValidarCodigoDTO(String tipoTransaccion, String codigoSeguridad) {
        this.tipoTransaccion = tipoTransaccion;
        this.codigoSeguridad = codigoSeguridad;
    }

    public SolicitudValidarCodigoDTO(String tipoTransaccion, String codigoSeguridad, String tokenSolicitud) {
        this.tipoTransaccion = tipoTransaccion;
        this.codigoSeguridad = codigoSeguridad;
        this.tokenSolicitud = tokenSolicitud;
    }

    public String getTipoTransaccion() {
        return tipoTransaccion;
    }

    public void setTipoTransaccion(String tipoTransaccion) {
        this.tipoTransaccion = tipoTransaccion;
    }

    public String getCodigoSeguridad() {
        return codigoSeguridad;
    }

    public void setCodigoSeguridad(String codigoSeguridad) {
        this.codigoSeguridad = codigoSeguridad;
    }

    public String getTokenSolicitud() {
        return tokenSolicitud;
    }

    public void setTokenSolicitud(String tokenSolicitud) {
        this.tokenSolicitud = tokenSolicitud;
    }
}

