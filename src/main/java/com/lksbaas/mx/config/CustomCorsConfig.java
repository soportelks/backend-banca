package com.lksbaas.mx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CustomCorsConfig implements WebMvcConfigurer {

        private static final List<String> ALLOWED_ORIGINS = List.of(
                        "http://localhost:4200",
                        "http://127.0.0.1:4200",
                        "https://lksbaas.com.mx",
                        "http://lksbaas.com.mx:9081",
                        "https://lksbaas.com.mx:9081");

        private static final List<String> ALLOWED_METHODS = List.of(
                        "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH");

        private static final List<String> ALLOWED_HEADERS = List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "Origin",
                        "X-Requested-With",
                        "Access-Control-Request-Method",
                        "Access-Control-Request-Headers");

        private static final List<String> EXPOSED_HEADERS = List.of(
                        "Authorization",
                        "Access-Control-Allow-Origin",
                        "Access-Control-Allow-Credentials");

        @Override
        public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                                .allowedOrigins(ALLOWED_ORIGINS.toArray(new String[0]))
                                .allowedMethods(ALLOWED_METHODS.toArray(new String[0]))
                                .allowedHeaders(ALLOWED_HEADERS.toArray(new String[0]))
                                .exposedHeaders(EXPOSED_HEADERS.toArray(new String[0]))
                                .allowCredentials(true)
                                .maxAge(3600);
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(ALLOWED_ORIGINS);
                config.setAllowedMethods(ALLOWED_METHODS);
                config.setAllowedHeaders(ALLOWED_HEADERS);
                config.setExposedHeaders(EXPOSED_HEADERS);
                config.setAllowCredentials(true);
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }
}
