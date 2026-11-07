package com.lksbaas.mx.controller;

import com.lksbaas.mx.exception.CodigoSeguridadException;
import com.lksbaas.mx.model.security.RespuestaCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.RespuestaValidarCodigoDTO;
import com.lksbaas.mx.model.security.SolicitudCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.SolicitudValidarCodigoDTO;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import com.lksbaas.mx.service.security.ICodigoSeguridadService;
import com.lksbaas.mx.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador REST para el mecanismo centralizado de códigos de seguridad para transacciones.
 * Expone endpoints para solicitar, validar y auditar tokens de autorización.
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/seguridad/codigo")
public class CodigoSeguridadController {

    private static final Logger log = LoggerFactory.getLogger(CodigoSeguridadController.class);

    @Autowired
    private ICodigoSeguridadService codigoSeguridadService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Endpoint para generar y enviar un código de seguridad para autorizar una transacción.
     *
     * @param solicitud Parámetros de la transacción
     * @param authHeader Encabezado Authorization opcional para resolución explícita
     * @return DTO de confirmación con correo enmascarado y vigencia
     */
    @PostMapping("/solicitar")
    public ResponseEntity<RespuestaCodigoSeguridadDTO> solicitarCodigo(
            @RequestBody SolicitudCodigoSeguridadDTO solicitud,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String usuario = extraerUsuarioAutenticado(authHeader);
            if (usuario == null || usuario.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaCodigoSeguridadDTO.error(401, "No se pudo identificar al usuario autenticado."));
            }

            RespuestaCodigoSeguridadDTO respuesta = codigoSeguridadService.solicitarCodigo(usuario, solicitud);
            if (respuesta.getCodigo() == 429) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(respuesta);
            }
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException | CodigoSeguridadException e) {
            log.warn("Error de validación al solicitar código de seguridad: {}", e.getMessage());
            return ResponseEntity.badRequest().body(RespuestaCodigoSeguridadDTO.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("Error no controlado al solicitar código de seguridad: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaCodigoSeguridadDTO.error(500, "Error interno al procesar la solicitud de código"));
        }
    }

    /**
     * Endpoint para validar el código de seguridad introducido por el usuario.
     * Si el código es correcto, emite un token de autorización para la transacción.
     *
     * @param solicitud Parámetros de validación (tipoTransaccion, codigoSeguridad, tokenSolicitud)
     * @param authHeader Encabezado Authorization opcional
     * @return DTO con el resultado de la validación
     */
    @PostMapping("/validar")
    public ResponseEntity<RespuestaValidarCodigoDTO> validarCodigo(
            @RequestBody SolicitudValidarCodigoDTO solicitud,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String usuario = extraerUsuarioAutenticado(authHeader);
            if (usuario == null || usuario.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaValidarCodigoDTO.fallo(401, "No se pudo identificar al usuario autenticado.", 0));
            }

            RespuestaValidarCodigoDTO respuesta = codigoSeguridadService.validarCodigo(usuario, solicitud);
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException | CodigoSeguridadException e) {
            log.warn("Error de validación al verificar código de seguridad: {}", e.getMessage());
            return ResponseEntity.badRequest().body(RespuestaValidarCodigoDTO.fallo(400, e.getMessage(), 0));
        } catch (Exception e) {
            log.error("Error no controlado al verificar código de seguridad: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaValidarCodigoDTO.fallo(500, "Error interno al verificar el código de seguridad", 0));
        }
    }

    /**
     * Endpoint para auditar la vigencia de un token de autorización emitido.
     */
    @PostMapping("/verificar-token")
    public ResponseEntity<Map<String, Object>> verificarToken(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String usuario = extraerUsuarioAutenticado(authHeader);
            if (usuario == null || usuario.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("valido", false, "mensaje", "Usuario no autenticado"));
            }

            String tipoStr = request.get("tipoTransaccion");
            String tokenAutorizacion = request.get("tokenAutorizacion");

            if (tipoStr == null || tokenAutorizacion == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("valido", false, "mensaje", "Parámetros insuficientes"));
            }

            TipoTransaccionSeguridad tipo = TipoTransaccionSeguridad.desdeCadena(tipoStr);
            boolean valido = codigoSeguridadService.validarTokenAutorizacion(usuario, tipo, tokenAutorizacion);

            return ResponseEntity.ok(Map.of(
                    "valido", valido,
                    "mensaje", valido ? "Token de autorización válido y vigente" : "Token inválido o expirado"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("valido", false, "mensaje", "Error al verificar token: " + e.getMessage()));
        }
    }

    private String extraerUsuarioAutenticado(String authHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtil.tokenValido(token)) {
                    return jwtUtil.extractUsername(token);
                }
            } catch (Exception e) {
                log.debug("No se pudo extraer usuario del encabezado Bearer: {}", e.getMessage());
            }
        }
        return null;
    }
}

