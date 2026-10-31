package com.lksbaas.mx.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.lksbaas.mx.model.Prospecto;
import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.util.JwtUtil;

import java.util.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@CrossOrigin("*")
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private ProspectoService prospectoService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String numero = req.get("numero_telefono");
        String password = req.get("contrasena");

        if (numero == null || password == null)
            return ResponseEntity.badRequest().body(
                    Map.of("codigo", 1, "mensaje", "Faltan datos"));

        Prospecto p = prospectoService.getProspecto(numero);

        if (p == null || !prospectoService.validarCredenciales(numero, password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("codigo", 1, "mensaje", "Credenciales inválidas"));
        }

        String tokenActual = p.getToken_sesion();

        if (tokenActual != null && jwtUtil.tokenValido(tokenActual)) {
            prospectoService.eliminarToken(numero);
        }

        // Validar estado del prospecto
        Map<String, Object> validacion = prospectoService.validadEstado(p.getCveGeneralesSolicitud());
        int codigoValidacion = (int) validacion.get("codigo");
        if (codigoValidacion != 0) {
            return ResponseEntity.ok(validacion);
        }

        prospectoService.actualizarInicioSesion(numero);

        // Generar nuevo token y guardarlo
        Map<String, Object> claims = new HashMap<>();
        claims.put("prospecto_id", p.getProspecto_id());
        claims.put("clave_prospecto", p.getClave_prospecto());
        claims.put("nombre_prospecto", p.getNombre_prospecto());
        claims.put("correo_electronico", p.getCorreo_electronico());
        claims.put("numero_telefono", p.getNumero_telefono());
        claims.put("domicilio", p.getDomicilio());
        claims.put("estatus_prospecto", p.getEstatus_prospecto());
        claims.put("cveGenerales", p.getCveGenerales());
        claims.put("numero_cliente", p.getNumero_cliente());
        claims.put("clabe", p.getClabe());
        claims.put("clabe_estatus", p.getClabe_estatus());
        claims.put("tipo_debito", p.getTipo_Debito());
        claims.put("clave_cliente", p.getClave_cliente());
        claims.put("solicitudId", p.getSolicitudId());
        claims.put("fechaSolicitud", p.getFechaSolicitud());
        claims.put("descripcionSolicitud", p.getDescripcionSolicitud());
        claims.put("observacionesSolicitud", p.getObservacionesSolicitud());
        claims.put("generalesIdSolicitud", p.getGeneralesIdSolicitud());
        claims.put("cveGeneralesSolicitud", p.getCveGeneralesSolicitud());
        claims.put("inicio_sesion", p.getInicio_sesion());
        claims.put("comision_spei", p.getComision_spei());
        claims.put("saldo_minimo", p.getSaldo_minimo());

        String newToken = jwtUtil.generateToken(claims, numero);
        prospectoService.guardarToken(numero, newToken);

        Map<String, Object> response = new HashMap<>();
        response.put("codigo", 0);
        response.put("mensaje", "Inicio de sesión exitoso");
        response.put("info", Map.of(
                "usuario", claims,
                "token", newToken));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
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