package com.lksbaas.mx.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.lksbaas.mx.model.Prospecto;
import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.util.JwtUtil;

import java.util.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final String KEY_CODIGO = "codigo";
    private static final String KEY_MENSAJE = "mensaje";


    private final ProspectoService prospectoService;

    private final JwtUtil jwtUtil;

    public AuthController(ProspectoService prospectoService, JwtUtil jwtUtil) {
        this.prospectoService = prospectoService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> req) {
        String numero = req.get("numero_telefono");
        String password = req.get("contrasena");

        if (numero == null || password == null)
            return ResponseEntity.badRequest().body(
                    Map.of(KEY_CODIGO, 1, KEY_MENSAJE, "Faltan datos"));

        Prospecto p = prospectoService.getProspecto(numero);

        if (p == null || !prospectoService.validarCredenciales(numero, password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of(KEY_CODIGO, 1, KEY_MENSAJE, "Credenciales inválidas"));
        }

        String tokenActual = p.getTokenSesion();

        if (tokenActual != null && jwtUtil.tokenValido(tokenActual)) {
            prospectoService.eliminarToken(numero);
        }

        // Validar estado del prospecto
        Map<String, Object> validacion = prospectoService.validadEstado(p.getCveGeneralesSolicitud());
        int codigoValidacion = (int) validacion.get(KEY_CODIGO);
        if (codigoValidacion != 0) {
            return ResponseEntity.ok(validacion);
        }

        prospectoService.actualizarInicioSesion(numero);

        // Generar nuevo token y guardarlo
        Map<String, Object> claims = new HashMap<>();
        claims.put("prospecto_id", p.getProspectoId());
        claims.put("clave_prospecto", p.getClaveProspecto());
        claims.put("nombre_prospecto", p.getNombreProspecto());
        claims.put("correo_electronico", p.getCorreoElectronico());
        claims.put("numero_telefono", p.getNumeroTelefono());
        claims.put("domicilio", p.getDomicilio());
        claims.put("estatus_prospecto", p.getEstatusProspecto());
        claims.put("cveGenerales", p.getCveGenerales());
        claims.put("numero_cliente", p.getNumeroCliente());
        claims.put("clabe", p.getClabe());
        claims.put("clabe_estatus", p.getClabeEstatus());
        claims.put("tipo_debito", p.getTipoDebito());
        claims.put("clave_cliente", p.getClaveCliente());
        claims.put("solicitudId", p.getSolicitudId());
        claims.put("fechaSolicitud", p.getFechaSolicitud());
        claims.put("descripcionSolicitud", p.getDescripcionSolicitud());
        claims.put("observacionesSolicitud", p.getObservacionesSolicitud());
        claims.put("generalesIdSolicitud", p.getGeneralesIdSolicitud());
        claims.put("cveGeneralesSolicitud", p.getCveGeneralesSolicitud());
        claims.put("inicio_sesion", p.getInicioSesion());
        claims.put("comision_spei", p.getComisionSpei());
        claims.put("saldo_minimo", p.getSaldoMinimo());

        String newToken = jwtUtil.generateToken(claims, numero);
        prospectoService.guardarToken(numero, newToken);

        Map<String, Object> response = new HashMap<>();
        response.put(KEY_CODIGO, 0);
        response.put(KEY_MENSAJE, "Inicio de sesión exitoso");
        response.put("info", Map.of(
                "usuario", claims,
                "token", newToken));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Token no proporcionado.");
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);
            prospectoService.eliminarToken(username);
            return ResponseEntity.ok("Sesión cerrada correctamente.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token inválido.");
        }
    }

}