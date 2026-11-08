package com.lksbaas.mx.config;

import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final String TXT_APPLICATION_JSON = "application/json";


    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;

    private final ProspectoService prospectoService;

    public JwtFilter(JwtUtil jwtUtil, ProspectoService prospectoService) {
        this.jwtUtil = jwtUtil;
        this.prospectoService = prospectoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Excepciones de endpoints públicos
        if (path.startsWith("/auth") ||
                path.startsWith("/onboarding") ||
                path.startsWith("/otp")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            responderNoAutorizado(response, "Token requerido.");
            return;
        }

        String token = authHeader.substring(7);
        String error;
        try {
            error = autenticar(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            error = "Sesión expirada. Inicia sesión nuevamente.";
        } catch (Exception e) {
            log.warn("Fallo de autenticacion JWT: {}", e.getClass().getSimpleName());
            error = "Error de autenticación";
        }

        if (error != null) {
            responderNoAutorizado(response, error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Valida el token y, si es correcto, establece la autenticación en el contexto.
     *
     * @return mensaje de error si el token no es válido; {@code null} si es válido.
     */
    private String autenticar(String token) {
        if (!jwtUtil.tokenValido(token)) {
            return "Token inválido o expirado.";
        }

        String numero = jwtUtil.extractUsername(token);
        if (numero == null) {
            return "Token inválido.";
        }

        String tokenGuardado = prospectoService.obtenerToken(numero);
        if (tokenGuardado == null || !token.equals(tokenGuardado)) {
            return "Sesión expirada. Inicia sesión nuevamente.";
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(numero,
                null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return null;
    }

    private void responderNoAutorizado(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(TXT_APPLICATION_JSON);
        response.getWriter().write("{\"codigo\":401,\"mensaje\":\"" + mensaje + "\"}");
    }
}
