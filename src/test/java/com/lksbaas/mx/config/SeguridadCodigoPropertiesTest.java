package com.lksbaas.mx.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeguridadCodigoPropertiesTest {

    @Test
    @DisplayName("Getters, setters y valores de configuración de SeguridadCodigoProperties")
    void testGettersYSetters() {
        SeguridadCodigoProperties props = new SeguridadCodigoProperties();

        props.setCorreoPrueba("test@dominio.com");
        assertEquals("test@dominio.com", props.getCorreoPrueba());

        props.setUsarCorreoPrueba(true);
        assertTrue(props.isUsarCorreoPrueba());

        props.setExpiracionMinutos(5);
        assertEquals(5, props.getExpiracionMinutos());

        props.setMaxIntentos(4);
        assertEquals(4, props.getMaxIntentos());

        props.setLongitud(8);
        assertEquals(8, props.getLongitud());

        props.setCooldownSegundos(30);
        assertEquals(30, props.getCooldownSegundos());
    }

    @Test
    @DisplayName("resolverCorreoDestino: Prioriza correo de pruebas cuando usarCorreoPrueba es true")
    void testResolverCorreoDestino_PruebaActiva() {
        SeguridadCodigoProperties props = new SeguridadCodigoProperties();
        props.setCorreoPrueba("prueba@banca.com");
        props.setUsarCorreoPrueba(true);

        assertEquals("prueba@banca.com", props.resolverCorreoDestino("real@cliente.com", "alt@cliente.com"));
    }

    @Test
    @DisplayName("resolverCorreoDestino: Usa alternativo o registrado cuando usarCorreoPrueba es false")
    void testResolverCorreoDestino_PruebaInactiva() {
        SeguridadCodigoProperties props = new SeguridadCodigoProperties();
        props.setUsarCorreoPrueba(false);

        assertEquals("alt@cliente.com", props.resolverCorreoDestino("real@cliente.com", "alt@cliente.com"));
        assertEquals("real@cliente.com", props.resolverCorreoDestino("real@cliente.com", null));
        assertEquals("real@cliente.com", props.resolverCorreoDestino("real@cliente.com", "   "));
        assertEquals("", props.resolverCorreoDestino(null, null));
    }

    @Test
    @DisplayName("enmascararCorreo: Enmascara correos validos y maneja nulos y casos borde")
    void testEnmascararCorreo() {
        assertEquals("", SeguridadCodigoProperties.enmascararCorreo(null));
        assertEquals("", SeguridadCodigoProperties.enmascararCorreo("   "));
        assertEquals("a@b.com", SeguridadCodigoProperties.enmascararCorreo("a@b.com"));
        assertEquals("a***@b.com", SeguridadCodigoProperties.enmascararCorreo("ab@b.com"));
        assertEquals("u***o@dominio.com", SeguridadCodigoProperties.enmascararCorreo("usuario@dominio.com"));
        assertEquals("c***e@banca.com", SeguridadCodigoProperties.enmascararCorreo("cliente@banca.com"));
    }
}
