package com.lksbaas.mx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Propiedades centralizadas de configuración para el mecanismo de códigos de seguridad.
 * Permite configurar tiempos de expiración, límites de intentos y el correo fijo para pruebas.
 */
@Component
public class SeguridadCodigoProperties {

    @Value("${seguridad.codigo.correo-prueba:contacto@multiplicandooportunidades.com}")
    private String correoPrueba;

    @Value("${seguridad.codigo.usar-correo-prueba:true}")
    private boolean usarCorreoPrueba;

    @Value("${seguridad.codigo.expiracion-minutos:2}")
    private int expiracionMinutos;

    @Value("${seguridad.codigo.max-intentos:3}")
    private int maxIntentos;

    @Value("${seguridad.codigo.longitud:6}")
    private int longitud;

    @Value("${seguridad.codigo.cooldown-segundos:60}")
    private int cooldownSegundos;

    public String getCorreoPrueba() {
        return correoPrueba;
    }

    public void setCorreoPrueba(String correoPrueba) {
        this.correoPrueba = correoPrueba;
    }

    public boolean isUsarCorreoPrueba() {
        return usarCorreoPrueba;
    }

    public void setUsarCorreoPrueba(boolean usarCorreoPrueba) {
        this.usarCorreoPrueba = usarCorreoPrueba;
    }

    public int getExpiracionMinutos() {
        return expiracionMinutos;
    }

    public void setExpiracionMinutos(int expiracionMinutos) {
        this.expiracionMinutos = expiracionMinutos;
    }

    public int getMaxIntentos() {
        return maxIntentos;
    }

    public void setMaxIntentos(int maxIntentos) {
        this.maxIntentos = maxIntentos;
    }

    public int getLongitud() {
        return longitud;
    }

    public void setLongitud(int longitud) {
        this.longitud = longitud;
    }

    public int getCooldownSegundos() {
        return cooldownSegundos;
    }

    public void setCooldownSegundos(int cooldownSegundos) {
        this.cooldownSegundos = cooldownSegundos;
    }

    /**
     * Determina el correo electrónico efectivo al cual se despachará la notificación.
     * Si la bandera de pruebas está activa y existe un correo de prueba configurado,
     * se prioriza dicho correo para asegurar un entorno de testing controlado.
     *
     * @param correoUsuario Correo registrado en la cuenta del usuario
     * @param correoAlternativo Correo alternativo opcional provisto en la solicitud
     * @return Dirección de correo final
     */
    public String resolverCorreoDestino(String correoUsuario, String correoAlternativo) {
        if (usarCorreoPrueba && correoPrueba != null && !correoPrueba.trim().isEmpty()) {
            return correoPrueba.trim();
        }
        if (correoAlternativo != null && !correoAlternativo.trim().isEmpty()) {
            return correoAlternativo.trim();
        }
        return correoUsuario != null ? correoUsuario.trim() : "";
    }

    /**
     * Enmascara un correo electrónico para exhibición segura en respuestas y registros.
     * Ejemplo: "usuario.banca@dominio.com" -> "u***a@dominio.com"
     *
     * @param email Correo electrónico
     * @return Cadena enmascarada
     */
    public static String enmascararCorreo(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "";
        }
        String correoLimpio = email.trim();
        int atIndex = correoLimpio.indexOf('@');
        if (atIndex <= 1) {
            return correoLimpio;
        }
        String usuario = correoLimpio.substring(0, atIndex);
        String dominio = correoLimpio.substring(atIndex);

        if (usuario.length() <= 2) {
            return usuario.charAt(0) + "***" + dominio;
        }
        return usuario.charAt(0) + "***" + usuario.charAt(usuario.length() - 1) + dominio;
    }
}

