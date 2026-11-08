package com.lksbaas.mx.model.security;

/**
 * DTO de respuesta tras validar un código de seguridad.
 * En caso de éxito, retorna el token de autorización de un solo uso para ejecutar la transacción.
 */
public class RespuestaValidarCodigoDTO {

    private int codigo; // 0 = éxito, 1 = código incorrecto, 2 = bloqueado, 3 = expirado, 4 = otro error
    private boolean valido;
    private String mensaje;
    private String tokenAutorizacion;
    private int intentosRestantes;

    public RespuestaValidarCodigoDTO() {
        // Constructor vacio requerido para la (de)serializacion JSON.
    }

    public static RespuestaValidarCodigoDTO exito(String mensaje, String tokenAutorizacion, int intentosRestantes) {
        RespuestaValidarCodigoDTO resp = new RespuestaValidarCodigoDTO();
        resp.setCodigo(0);
        resp.setValido(true);
        resp.setMensaje(mensaje);
        resp.setTokenAutorizacion(tokenAutorizacion);
        resp.setIntentosRestantes(intentosRestantes);
        return resp;
    }

    public static RespuestaValidarCodigoDTO fallo(int codigo, String mensaje, int intentosRestantes) {
        RespuestaValidarCodigoDTO resp = new RespuestaValidarCodigoDTO();
        resp.setCodigo(codigo);
        resp.setValido(false);
        resp.setMensaje(mensaje);
        resp.setTokenAutorizacion(null);
        resp.setIntentosRestantes(intentosRestantes);
        return resp;
    }

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }

    public boolean isValido() {
        return valido;
    }

    public void setValido(boolean valido) {
        this.valido = valido;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getTokenAutorizacion() {
        return tokenAutorizacion;
    }

    public void setTokenAutorizacion(String tokenAutorizacion) {
        this.tokenAutorizacion = tokenAutorizacion;
    }

    public int getIntentosRestantes() {
        return intentosRestantes;
    }

    public void setIntentosRestantes(int intentosRestantes) {
        this.intentosRestantes = intentosRestantes;
    }
}

