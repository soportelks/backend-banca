package com.lksbaas.mx.model.security;

/**
 * DTO de respuesta tras solicitar la generación de un código de seguridad.
 * No incluye el código en texto plano por motivos estrictos de seguridad.
 */
public class RespuestaCodigoSeguridadDTO {

    private int codigo; // 0 = éxito, distinto de 0 = error
    private String mensaje;
    private String destinatarioEnmascarado;
    private String tipoTransaccion;
    private long expiracionSegundos;
    private String tokenSolicitud;

    public RespuestaCodigoSeguridadDTO() {
    }

    public RespuestaCodigoSeguridadDTO(int codigo, String mensaje) {
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    public static RespuestaCodigoSeguridadDTO exito(String mensaje, String destinatarioEnmascarado,
                                                    String tipoTransaccion, long expiracionSegundos,
                                                    String tokenSolicitud) {
        RespuestaCodigoSeguridadDTO resp = new RespuestaCodigoSeguridadDTO(0, mensaje);
        resp.setDestinatarioEnmascarado(destinatarioEnmascarado);
        resp.setTipoTransaccion(tipoTransaccion);
        resp.setExpiracionSegundos(expiracionSegundos);
        resp.setTokenSolicitud(tokenSolicitud);
        return resp;
    }

    public static RespuestaCodigoSeguridadDTO error(int codigo, String mensaje) {
        return new RespuestaCodigoSeguridadDTO(codigo, mensaje);
    }

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getDestinatarioEnmascarado() {
        return destinatarioEnmascarado;
    }

    public void setDestinatarioEnmascarado(String destinatarioEnmascarado) {
        this.destinatarioEnmascarado = destinatarioEnmascarado;
    }

    public String getTipoTransaccion() {
        return tipoTransaccion;
    }

    public void setTipoTransaccion(String tipoTransaccion) {
        this.tipoTransaccion = tipoTransaccion;
    }

    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }

    public void setExpiracionSegundos(long expiracionSegundos) {
        this.expiracionSegundos = expiracionSegundos;
    }

    public String getTokenSolicitud() {
        return tokenSolicitud;
    }

    public void setTokenSolicitud(String tokenSolicitud) {
        this.tokenSolicitud = tokenSolicitud;
    }
}

