package com.lksbaas.mx.model.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CodigoTransaccionTest {

    @Test
    @DisplayName("CodigoTransaccion constructor y valores iniciales")
    void testConstructorYValoresIniciales() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime expiracion = ahora.plusMinutes(2);

        CodigoTransaccion ct = new CodigoTransaccion(
                "5512345678",
                "test@banca.com",
                TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                "$2a$10$hash",
                "tok-req-1",
                3,
                ahora,
                expiracion
        );

        ct.setId(500L);
        assertEquals(500L, ct.getId());
        assertEquals("5512345678", ct.getIdentificadorUsuario());
        assertEquals("test@banca.com", ct.getCorreoDestino());
        assertEquals(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, ct.getTipoTransaccion());
        assertEquals("$2a$10$hash", ct.getCodigoHash());
        assertEquals("tok-req-1", ct.getTokenSolicitud());
        assertEquals("PENDIENTE", ct.getEstado());
        assertEquals(0, ct.getIntentosFallidos());
        assertEquals(3, ct.getMaxIntentos());
        assertEquals(ahora, ct.getFechaCreacion());
        assertEquals(expiracion, ct.getFechaExpiracion());
        assertTrue(ct.estaPendiente());
        assertFalse(ct.estaExpirado(ahora));
        assertFalse(ct.estaBloqueado());
        assertFalse(ct.estaUsado());
    }

    @Test
    @DisplayName("CodigoTransaccion setters y transiciones de estado")
    void testSettersYEstados() {
        CodigoTransaccion ct = new CodigoTransaccion();
        LocalDateTime ahora = LocalDateTime.now();

        ct.setIdentificadorUsuario("5599887766");
        assertEquals("5599887766", ct.getIdentificadorUsuario());

        ct.setCorreoDestino("otro@banca.com");
        assertEquals("otro@banca.com", ct.getCorreoDestino());

        ct.setTipoTransaccion(TipoTransaccionSeguridad.TRASPASO);
        assertEquals(TipoTransaccionSeguridad.TRASPASO, ct.getTipoTransaccion());

        ct.setCodigoHash("newhash");
        assertEquals("newhash", ct.getCodigoHash());

        ct.setTokenSolicitud("newtok");
        assertEquals("newtok", ct.getTokenSolicitud());

        ct.setTokenAutorizacion("auth-123");
        assertEquals("auth-123", ct.getTokenAutorizacion());

        ct.setIntentosFallidos(3);
        ct.setMaxIntentos(3);
        assertTrue(ct.estaBloqueado());

        ct.setEstado("BLOQUEADO");
        assertTrue(ct.estaBloqueado());

        ct.setEstado("USADO");
        assertTrue(ct.estaUsado());

        ct.setFechaConsumo(ahora);
        assertEquals(ahora, ct.getFechaConsumo());

        ct.setFechaExpiracion(ahora.minusMinutes(1));
        assertTrue(ct.estaExpirado(ahora));
    }
}
