package com.lksbaas.mx.dao.security;

import com.lksbaas.mx.model.security.CodigoTransaccion;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;

import java.util.Optional;

/**
 * Interfaz de acceso a datos para la persistencia y control transaccional
 * de los códigos de seguridad.
 */
public interface ICodigoTransaccionDAO {

    /**
     * Inserta un nuevo registro de código de transacción.
     */
    void guardar(CodigoTransaccion codigo);

    /**
     * Obtiene el código pendiente más reciente para un usuario y tipo de transacción,
     * aplicando bloqueo pesimista a nivel de fila (FOR UPDATE) para garantizar atomicidad.
     */
    Optional<CodigoTransaccion> obtenerPendienteParaActualizacion(String identificadorUsuario,
                                                                 TipoTransaccionSeguridad tipoTransaccion);

    /**
     * Obtiene un código pendiente mediante su token de solicitud y datos asociados con bloqueo pesimista.
     */
    Optional<CodigoTransaccion> obtenerPorTokenSolicitudParaActualizacion(String identificadorUsuario,
                                                                         TipoTransaccionSeguridad tipoTransaccion,
                                                                         String tokenSolicitud);

    /**
     * Obtiene el último código emitido (en cualquier estado) para evaluar cooldowns y prevenir abuso.
     */
    Optional<CodigoTransaccion> obtenerUltimoPorUsuarioYTipo(String identificadorUsuario,
                                                            TipoTransaccionSeguridad tipoTransaccion);

    /**
     * Invalida los códigos pendientes anteriores de un usuario para un tipo de transacción.
     */
    void invalidarPendientesAnteriores(String identificadorUsuario, TipoTransaccionSeguridad tipoTransaccion);

    /**
     * Actualiza el conteo de intentos fallidos y bloquea si corresponde.
     */
    void registrarIntentoFallido(Long id, int nuevosIntentos, boolean bloquear);

    /**
     * Marca el código como utilizado exitosamente y asocia el token de autorización generado.
     */
    void marcarComoUsado(Long id, String tokenAutorizacion);

    /**
     * Marca el código como expirado.
     */
    void marcarComoExpirado(Long id);

    /**
     * Valida la autenticidad y vigencia de un token de autorización previamente expedido.
     */
    boolean validarTokenAutorizacion(String identificadorUsuario,
                                     TipoTransaccionSeguridad tipoTransaccion,
                                     String tokenAutorizacion);

    /**
     * Invalida o consume un token de autorización para evitar que se use más de una vez en la transacción.
     */
    boolean consumirTokenAutorizacion(String identificadorUsuario,
                                      TipoTransaccionSeguridad tipoTransaccion,
                                      String tokenAutorizacion);
}

