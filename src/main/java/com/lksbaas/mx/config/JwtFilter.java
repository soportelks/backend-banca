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
            String numero = jwtUtil.extractUsername(token);

            if (numero != null && jwtUtil.tokenValido(token)) {
                String tokenGuardado = prospectoService.obtenerToken(numero);

                if (tokenGuardado != null && token.equals(tokenGuardado)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(numero,
                            null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Sesión expirada. Inicia sesión nuevamente.");
                    return;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Token inválido.");
                return;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token requerido.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
