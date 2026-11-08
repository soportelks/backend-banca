package com.lksbaas.mx.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    private final CustomCorsConfig corsConfig = new CustomCorsConfig();

    @Test
    @DisplayName("CorsConfigurationSource resuelve origenes dinamicos y credenciales correctamente")
    void testCorsConfigurationSource() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/auth/login");
        request.addHeader("Origin", "http://localhost:4200");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);
        assertNotNull(config.getAllowCredentials());
        assertTrue(config.getAllowCredentials());
        assertTrue(config.getAllowedOrigins().contains("http://localhost:4200"));
        assertTrue(config.getAllowedOrigins().contains("https://lksbaas.com.mx"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("GET"));
        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
        assertTrue(config.getExposedHeaders().contains("Authorization"));
    }

    @Test
    @DisplayName("addCorsMappings registra mapeo global sin errores")
    void testAddCorsMappings() {
        CorsRegistry registry = new CorsRegistry();
        assertDoesNotThrow(() -> corsConfig.addCorsMappings(registry));
    }
}
