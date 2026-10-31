package com.lksbaas.mx.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inicializador de base de datos para tablas y secuencias de control SPEI
 * Permite garantizar idempotencia y prevenir dispersiones masivas duplicadas.
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("Verificando / Inicializando tablas y secuencias de control SPEI...");

            jdbcTemplate.execute("""
                CREATE SCHEMA IF NOT EXISTS spei;

                CREATE TABLE IF NOT EXISTS spei.lote_dispersion (
                    id BIGSERIAL PRIMARY KEY,
                    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
                    cve_cliente VARCHAR(50) NOT NULL,
                    total_ordenes INT NOT NULL,
                    monto_total NUMERIC(16,2) NOT NULL,
                    estatus VARCHAR(30) NOT NULL,
                    respuesta_json TEXT,
                    fecha_creacion TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
                    fecha_actualizacion TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
                );

                CREATE INDEX IF NOT EXISTS idx_lote_dispersion_key ON spei.lote_dispersion(idempotency_key);

                CREATE SEQUENCE IF NOT EXISTS spei.seq_clave_rastreo START WITH 1 INCREMENT BY 1;
            """);

            log.info("Tablas y secuencias de control SPEI inicializadas exitosamente.");
        } catch (Exception e) {
            log.warn("Aviso al inicializar tablas de control SPEI (podrían existir ya): {}", e.getMessage());
        }
    }
}

