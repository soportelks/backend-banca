package com.lksbaas.mx.service.security;

import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;

/**
 * Abstracción de canal de notificación para códigos de seguridad.
 * Diseñada siguiendo el principio de inversión de dependencias y abierto/cerrado (SOLID),
 * permitiendo incorporar futuros proveedores (SMS, WhatsApp, Push, Gateway)
 * sin modificar la lógica central de seguridad.
 */
public interface INotificadorCodigoSeguridad {

    /**
     * Envía la notificación con el código de seguridad al destinatario final.
     *
     * @param destinatario Dirección de correo o teléfono de destino
     * @param codigo Código de seguridad en texto plano (sólo para renderizado en mensaje)
     * @param tipoTransaccion Tipo de transacción bancaria a autorizar
     * @param minutosExpiracion Tiempo de vigencia del código
     * @param esEntornoPrueba Indica si la notificación fue redirigida a un correo de pruebas
     */
    void enviarCodigo(String destinatario, String codigo,
                       TipoTransaccionSeguridad tipoTransaccion,
                       int minutosExpiracion, boolean esEntornoPrueba);
}

