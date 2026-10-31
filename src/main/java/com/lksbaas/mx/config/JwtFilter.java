package com.lksbaas.mx.config;

import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ProspectoService prospectoService;

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
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                if (!jwtUtil.tokenValido(token)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"codigo\":401,\"mensaje\":\"Token inválido o expirado.\"}");
                    return;
                }

                String numero = jwtUtil.extractUsername(token);

                if (numero != null) {
                    String tokenGuardado = prospectoService.obtenerToken(numero);

                    if (tokenGuardado != null && token.equals(tokenGuardado)) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(numero,
                                null, Collections.emptyList());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"codigo\":401,\"mensaje\":\"Sesión expirada. Inicia sesión nuevamente.\"}");
                        return;
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"codigo\":401,\"mensaje\":\"Token inválido.\"}");
                    return;
                }
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"codigo\":401,\"mensaje\":\"Sesión expirada. Inicia sesión nuevamente.\"}");
                return;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"codigo\":401,\"mensaje\":\"Error de autenticación: " + e.getMessage() + "\"}");
                return;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"codigo\":401,\"mensaje\":\"Token requerido.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
