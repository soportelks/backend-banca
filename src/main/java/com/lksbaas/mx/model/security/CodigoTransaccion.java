package com.lksbaas.mx.model.security;

import java.time.LocalDateTime;

/**
 * Representa el registro de persistencia de un código de seguridad para transacciones.
 * Almacena el hash seguro del código (nunca texto plano), los tokens de correlación y autorización,
 * así como el estado y control de intentos fallidos.
 */
public class CodigoTransaccion {

    private Long id;
    private String identificadorUsuario;
    private String correoDestino;
    private TipoTransaccionSeguridad tipoTransaccion;
    private String codigoHash;
    private String tokenSolicitud;
    private String tokenAutorizacion;
    private String estado; // PENDIENTE, USADO, EXPIRADO, BLOQUEADO
    private int intentosFallidos;
    private int maxIntentos;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaExpiracion;
    private LocalDateTime fechaConsumo;

    public CodigoTransaccion() {
    }

    public CodigoTransaccion(String identificadorUsuario, String correoDestino,
                             TipoTransaccionSeguridad tipoTransaccion, String codigoHash,
                             String tokenSolicitud, int maxIntentos,
                             LocalDateTime fechaCreacion, LocalDateTime fechaExpiracion) {
        this.identificadorUsuario = identificadorUsuario;
        this.correoDestino = correoDestino;
        this.tipoTransaccion = tipoTransaccion;
        this.codigoHash = codigoHash;
        this.tokenSolicitud = tokenSolicitud;
        this.estado = "PENDIENTE";
        this.intentosFallidos = 0;
        this.maxIntentos = maxIntentos;
        this.fechaCreacion = fechaCreacion;
        this.fechaExpiracion = fechaExpiracion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentificadorUsuario() {
        return identificadorUsuario;
    }

    public void setIdentificadorUsuario(String identificadorUsuario) {
        this.identificadorUsuario = identificadorUsuario;
    }

    public String getCorreoDestino() {
        return correoDestino;
    }

    public void setCorreoDestino(String correoDestino) {
        this.correoDestino = correoDestino;
    }

    public TipoTransaccionSeguridad getTipoTransaccion() {
        return tipoTransaccion;
    }

    public void setTipoTransaccion(TipoTransaccionSeguridad tipoTransaccion) {
        this.tipoTransaccion = tipoTransaccion;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public void setCodigoHash(String codigoHash) {
        this.codigoHash = codigoHash;
    }

    public String getTokenSolicitud() {
        return tokenSolicitud;
    }

    public void setTokenSolicitud(String tokenSolicitud) {
        this.tokenSolicitud = tokenSolicitud;
    }

    public String getTokenAutorizacion() {
        return tokenAutorizacion;
    }

    public void setTokenAutorizacion(String tokenAutorizacion) {
        this.tokenAutorizacion = tokenAutorizacion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(int intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public int getMaxIntentos() {
        return maxIntentos;
    }

    public void setMaxIntentos(int maxIntentos) {
        this.maxIntentos = maxIntentos;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public void setFechaExpiracion(LocalDateTime fechaExpiracion) {
        this.fechaExpiracion = fechaExpiracion;
    }

    public LocalDateTime getFechaConsumo() {
        return fechaConsumo;
    }

    public void setFechaConsumo(LocalDateTime fechaConsumo) {
        this.fechaConsumo = fechaConsumo;
    }

    public boolean estaExpirado(LocalDateTime ahora) {
        return fechaExpiracion == null || ahora.isAfter(fechaExpiracion);
    }

    public boolean estaBloqueado() {
        return "BLOQUEADO".equalsIgnoreCase(estado) || intentosFallidos >= maxIntentos;
    }

    public boolean estaUsado() {
        return "USADO".equalsIgnoreCase(estado);
    }

    public boolean estaPendiente() {
        return "PENDIENTE".equalsIgnoreCase(estado);
    }
}

