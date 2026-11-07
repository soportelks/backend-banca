package com.lksbaas.mx.service.security;

import com.lksbaas.mx.model.security.RespuestaCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.RespuestaValidarCodigoDTO;
import com.lksbaas.mx.model.security.SolicitudCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.SolicitudValidarCodigoDTO;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;

/**
 * Contrato principal del servicio de códigos de seguridad para transacciones bancarias.
 * Centraliza la lógica de generación criptográfica, hashing, validación atómica y tokens de autorización.
 */
public interface ICodigoSeguridadService {

    /**
     * Genera un nuevo código de seguridad criptográfico, lo asocia al usuario y tipo de transacción,
     * almacena su hash y lo envía a través del canal de notificación correspondiente.
     *
     * @param identificadorUsuario Identificador del usuario (teléfono, clave prospecto o cliente extraído del JWT)
     * @param solicitud Parámetros de la solicitud
     * @return Respuesta estructurada sin el código en texto plano
     */
    RespuestaCodigoSeguridadDTO solicitarCodigo(String identificadorUsuario, SolicitudCodigoSeguridadDTO solicitud);

    /**
     * Valida de manera atómica el código ingresado por el usuario, evitando condiciones de carrera
     * y mitigando ataques de fuerza bruta mediante conteo estricto de intentos.
     *
     * @param identificadorUsuario Identificador del usuario autenticado
     * @param solicitud Parámetros con el tipo de transacción y código ingresado
     * @return Resultado de la validación y, en caso de éxito, el token de autorización de un solo uso
     */
    RespuestaValidarCodigoDTO validarCodigo(String identificadorUsuario, SolicitudValidarCodigoDTO solicitud);

    /**
     * Verifica que exista un token de autorización válido y vigente para el usuario y tipo de transacción.
     */
    boolean validarTokenAutorizacion(String identificadorUsuario, TipoTransaccionSeguridad tipoTransaccion, String tokenAutorizacion);

    /**
     * Consume el token de autorización para evitar su reutilización al ejecutar la transacción final.
     */
    boolean consumirTokenAutorizacion(String identificadorUsuario, TipoTransaccionSeguridad tipoTransaccion, String tokenAutorizacion);
}

