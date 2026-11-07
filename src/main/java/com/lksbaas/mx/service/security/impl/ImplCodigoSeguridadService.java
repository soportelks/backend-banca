package com.lksbaas.mx.service.security.impl;

import com.lksbaas.mx.config.SeguridadCodigoProperties;
import com.lksbaas.mx.dao.security.ICodigoTransaccionDAO;
import com.lksbaas.mx.exception.CodigoSeguridadException;
import com.lksbaas.mx.model.Prospecto;
import com.lksbaas.mx.model.security.CodigoTransaccion;
import com.lksbaas.mx.model.security.RespuestaCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.RespuestaValidarCodigoDTO;
import com.lksbaas.mx.model.security.SolicitudCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.SolicitudValidarCodigoDTO;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.service.security.ICodigoSeguridadService;
import com.lksbaas.mx.service.security.INotificadorCodigoSeguridad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio centralizado para la emisión y verificación de códigos de seguridad transaccionales.
 * 
 * Garantiza:
 * - Generación criptográficamente segura (SecureRandom).
 * - Almacenamiento protegido mediante hash irreversible con salting (BCrypt).
 * - Validación atómica mediante bloqueos a nivel de base de datos.
 * - Prevención de fuerza bruta con bloqueo por número de intentos fallidos.
 * - Aislamiento estricto por usuario y tipo de transacción.
 */
@Service
public class ImplCodigoSeguridadService implements ICodigoSeguridadService {

    private static final Logger log = LoggerFactory.getLogger(ImplCodigoSeguridadService.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private ICodigoTransaccionDAO dao;

    @Autowired
    private INotificadorCodigoSeguridad notificador;

    @Autowired
    private SeguridadCodigoProperties properties;

    @Autowired
    private ProspectoService prospectoService;

    @Override
    @Transactional
    public RespuestaCodigoSeguridadDTO solicitarCodigo(String identificadorUsuario, SolicitudCodigoSeguridadDTO solicitud) {
        if (identificadorUsuario == null || identificadorUsuario.trim().isEmpty()) {
            throw new CodigoSeguridadException("Identificador de usuario no proporcionado");
        }
        if (solicitud == null) {
            solicitud = new SolicitudCodigoSeguridadDTO();
        }

        TipoTransaccionSeguridad tipo = TipoTransaccionSeguridad.desdeCadena(solicitud.getTipoTransaccion());
        LocalDateTime ahora = LocalDateTime.now();

        // 1. Control de frecuencia / Cooldown para evitar saturación y spam
        Optional<CodigoTransaccion> ultimoCodigo = dao.obtenerUltimoPorUsuarioYTipo(identificadorUsuario, tipo);
        if (ultimoCodigo.isPresent()) {
            LocalDateTime creacionPrevia = ultimoCodigo.get().getFechaCreacion();
            long segundosTranscurridos = Duration.between(creacionPrevia, ahora).getSeconds();
            int cooldown = properties.getCooldownSegundos();
            if (segundosTranscurridos < cooldown) {
                long segundosRestantes = cooldown - segundosTranscurridos;
                log.warn("Solicitud de código rechazada por cooldown para usuario {}. Faltan {} segundos.",
                        identificadorUsuario, segundosRestantes);
                return RespuestaCodigoSeguridadDTO.error(429,
                        "Por favor espera " + segundosRestantes + " segundos antes de solicitar un nuevo código de seguridad.");
            }
        }

        // 2. Resolver correo del usuario y aplicar configuración de pruebas
        String correoUsuario = null;
        try {
            Prospecto prospecto = prospectoService.getProspecto(identificadorUsuario);
            if (prospecto != null) {
                correoUsuario = prospecto.getCorreo_electronico();
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el perfil de prospecto para el usuario {}: {}", identificadorUsuario, e.getMessage());
        }

        String correoDestino = properties.resolverCorreoDestino(correoUsuario, solicitud.getCorreoAlternativo());
        if (correoDestino == null || correoDestino.trim().isEmpty()) {
            throw new CodigoSeguridadException("No fue posible determinar un correo electrónico de destino para el usuario");
        }

        // 3. Invalidar cualquier código pendiente previo para este usuario y tipo de transacción
        dao.invalidarPendientesAnteriores(identificadorUsuario, tipo);

        // 4. Generación criptográfica del código numérico
        int longitud = Math.max(4, Math.min(properties.getLongitud(), 8)); // Entre 4 y 8 dígitos
        String codigoPlano = generarCodigoNumericoSeguro(longitud);

        // 5. Hashing seguro con BCrypt (nunca guardar el código plano en base de datos)
        String hash = passwordEncoder.encode(codigoPlano);

        // 6. Preparar y persistir el registro del código
        String tokenSolicitud = UUID.randomUUID().toString();
        int minutosExpiracion = properties.getExpiracionMinutos();
        LocalDateTime fechaExpiracion = ahora.plusMinutes(minutosExpiracion);

        CodigoTransaccion nuevoCodigo = new CodigoTransaccion(
                identificadorUsuario,
                correoDestino,
                tipo,
                hash,
                tokenSolicitud,
                properties.getMaxIntentos(),
                ahora,
                fechaExpiracion
        );
        dao.guardar(nuevoCodigo);

        // 7. Despachar notificación
        boolean esPrueba = properties.isUsarCorreoPrueba();
        notificador.enviarCodigo(correoDestino, codigoPlano, tipo, minutosExpiracion, esPrueba);

        long expiracionSegundos = Duration.between(ahora, fechaExpiracion).getSeconds();
        String correoEnmascarado = SeguridadCodigoProperties.enmascararCorreo(correoDestino);

        log.info("Código de seguridad transaccional ({}) emitido exitosamente para el usuario {}. Expira en {} segundos.",
                tipo.name(), identificadorUsuario, expiracionSegundos);

        return RespuestaCodigoSeguridadDTO.exito(
                "Código de seguridad enviado exitosamente",
                correoEnmascarado,
                tipo.name(),
                expiracionSegundos,
                tokenSolicitud
        );
    }

    @Override
    @Transactional
    public RespuestaValidarCodigoDTO validarCodigo(String identificadorUsuario, SolicitudValidarCodigoDTO solicitud) {
        if (identificadorUsuario == null || identificadorUsuario.trim().isEmpty()) {
            return RespuestaValidarCodigoDTO.fallo(1, "Identificador de usuario no proporcionado", 0);
        }
        if (solicitud == null) {
            return RespuestaValidarCodigoDTO.fallo(1, "Solicitud no proporcionada", 0);
        }
        if (solicitud.getCodigoSeguridad() == null || solicitud.getCodigoSeguridad().trim().isEmpty()) {
            return RespuestaValidarCodigoDTO.fallo(1, "El código de seguridad es obligatorio", 0);
        }

        TipoTransaccionSeguridad tipo = TipoTransaccionSeguridad.desdeCadena(solicitud.getTipoTransaccion());
        String codigoIngresado = solicitud.getCodigoSeguridad().trim();
        LocalDateTime ahora = LocalDateTime.now();

        // Obtener el registro con bloqueo pesimista a nivel de base de datos
        Optional<CodigoTransaccion> optCodigo;
        if (solicitud.getTokenSolicitud() != null && !solicitud.getTokenSolicitud().trim().isEmpty()) {
            optCodigo = dao.obtenerPorTokenSolicitudParaActualizacion(
                    identificadorUsuario, tipo, solicitud.getTokenSolicitud().trim());
        } else {
            optCodigo = dao.obtenerPendienteParaActualizacion(identificadorUsuario, tipo);
        }

        if (optCodigo.isEmpty()) {
            log.warn("Intento de validación sin código pendiente para usuario {} y tipo {}", identificadorUsuario, tipo.name());
            return RespuestaValidarCodigoDTO.fallo(1, "No existe un código de seguridad activo para esta transacción. Solicite uno nuevo.", 0);
        }

        CodigoTransaccion codigo = optCodigo.get();

        // Validar si ya está bloqueado
        if (codigo.estaBloqueado()) {
            log.warn("Intento de uso de código bloqueado para usuario {} en {}", identificadorUsuario, tipo.name());
            return RespuestaValidarCodigoDTO.fallo(2, "El código de seguridad ha sido bloqueado por exceder el número máximo de intentos permitidos.", 0);
        }

        // Validar si está expirado
        if (codigo.estaExpirado(ahora)) {
            dao.marcarComoExpirado(codigo.getId());
            log.info("Código de seguridad expirado para usuario {} en {}", identificadorUsuario, tipo.name());
            return RespuestaValidarCodigoDTO.fallo(3, "El código de seguridad ha expirado. Por favor solicita uno nuevo.", 0);
        }

        // Validar si ya fue usado (defensa en profundidad)
        if (!codigo.estaPendiente()) {
            log.warn("Intento de reutilización de código ya procesado ({}) para usuario {}", codigo.getEstado(), identificadorUsuario);
            return RespuestaValidarCodigoDTO.fallo(1, "El código de seguridad ya no es válido.", 0);
        }

        // Comparar el hash criptográfico del código
        boolean coincide = passwordEncoder.matches(codigoIngresado, codigo.getCodigoHash());

        if (coincide) {
            // Generar token de autorización único
            String tokenAutorizacion = UUID.randomUUID().toString();
            dao.marcarComoUsado(codigo.getId(), tokenAutorizacion);

            int intentosRestantes = Math.max(0, codigo.getMaxIntentos() - codigo.getIntentosFallidos());
            log.info("Código de seguridad verificado exitosamente para usuario {} en {}", identificadorUsuario, tipo.name());
            return RespuestaValidarCodigoDTO.exito("Código validado exitosamente", tokenAutorizacion, intentosRestantes);
        } else {
            // Manejo estricto de intentos fallidos
            int nuevosIntentos = codigo.getIntentosFallidos() + 1;
            boolean bloquear = nuevosIntentos >= codigo.getMaxIntentos();
            dao.registrarIntentoFallido(codigo.getId(), nuevosIntentos, bloquear);

            int intentosRestantes = Math.max(0, codigo.getMaxIntentos() - nuevosIntentos);
            if (bloquear) {
                log.warn("Código de seguridad bloqueado por fuerza bruta para usuario {} en {}", identificadorUsuario, tipo.name());
                return RespuestaValidarCodigoDTO.fallo(2, "Código incorrecto. Ha alcanzado el límite de intentos permitidos y el código ha sido bloqueado.", 0);
            } else {
                log.info("Intento fallido de validación de código para usuario {}. Intentos restantes: {}", identificadorUsuario, intentosRestantes);
                return RespuestaValidarCodigoDTO.fallo(1, "Código incorrecto. Te quedan " + intentosRestantes + " intento(s).", intentosRestantes);
            }
        }
    }

    @Override
    public boolean validarTokenAutorizacion(String identificadorUsuario, TipoTransaccionSeguridad tipoTransaccion, String tokenAutorizacion) {
        if (identificadorUsuario == null || tipoTransaccion == null || tokenAutorizacion == null) {
            return false;
        }
        return dao.validarTokenAutorizacion(identificadorUsuario, tipoTransaccion, tokenAutorizacion);
    }

    @Override
    @Transactional
    public boolean consumirTokenAutorizacion(String identificadorUsuario, TipoTransaccionSeguridad tipoTransaccion, String tokenAutorizacion) {
        if (identificadorUsuario == null || tipoTransaccion == null || tokenAutorizacion == null) {
            return false;
        }
        return dao.consumirTokenAutorizacion(identificadorUsuario, tipoTransaccion, tokenAutorizacion);
    }

    private String generarCodigoNumericoSeguro(int longitud) {
        int min = (int) Math.pow(10, longitud - 1);
        int max = (int) Math.pow(10, longitud) - 1;
        int valor = min + secureRandom.nextInt(max - min + 1);
        return String.valueOf(valor);
    }
}

