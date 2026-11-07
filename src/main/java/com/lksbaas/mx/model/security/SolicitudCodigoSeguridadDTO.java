package com.lksbaas.mx.model.security;

import java.util.Map;

/**
 * DTO para la solicitud de generación de un código de seguridad para transacciones.
 */
public class SolicitudCodigoSeguridadDTO {

    /**
     * Tipo de transacción u operación financiera (e.g. TRANSFERENCIA_SPEI, TRASPASO).
     */
    private String tipoTransaccion;

    /**
     * Correo alternativo opcional en caso de que se permita override a nivel de negocio.
     * Si no se proporciona, se toma el correo registrado del usuario o el correo de pruebas configurado.
     */
    private String correoAlternativo;

    /**
     * Metadatos opcionales contextuales de la transacción (monto, cuenta destino, etc.) para auditoría.
     */
    private Map<String, Object> metadatos;

    public SolicitudCodigoSeguridadDTO() {
    }

    public SolicitudCodigoSeguridadDTO(String tipoTransaccion) {
        this.tipoTransaccion = tipoTransaccion;
    }

    public SolicitudCodigoSeguridadDTO(String tipoTransaccion, String correoAlternativo) {
        this.tipoTransaccion = tipoTransaccion;
        this.correoAlternativo = correoAlternativo;
    }

    public String getTipoTransaccion() {
        return tipoTransaccion;
    }

    public void setTipoTransaccion(String tipoTransaccion) {
        this.tipoTransaccion = tipoTransaccion;
    }

    public String getCorreoAlternativo() {
        return correoAlternativo;
    }

    public void setCorreoAlternativo(String correoAlternativo) {
        this.correoAlternativo = correoAlternativo;
    }

    public Map<String, Object> getMetadatos() {
        return metadatos;
    }

    public void setMetadatos(Map<String, Object> metadatos) {
        this.metadatos = metadatos;
    }
}

