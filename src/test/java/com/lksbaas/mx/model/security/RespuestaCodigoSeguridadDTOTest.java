package com.lksbaas.mx.model.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespuestaCodigoSeguridadDTOTest {

    @Test
    @DisplayName("RespuestaCodigoSeguridadDTO constructores, getters y setters")
    void testDTO() {
        RespuestaCodigoSeguridadDTO dto = new RespuestaCodigoSeguridadDTO();
        dto.setCodigo(0);
        dto.setMensaje("Ok");
        dto.setDestinatarioEnmascarado("a***b@c.com");
        dto.setTipoTransaccion("SPEI");
        dto.setExpiracionSegundos(60L);
        dto.setTokenSolicitud("tok-1");

        assertEquals(0, dto.getCodigo());
        assertEquals("Ok", dto.getMensaje());
        assertEquals("a***b@c.com", dto.getDestinatarioEnmascarado());
        assertEquals("SPEI", dto.getTipoTransaccion());
        assertEquals(60L, dto.getExpiracionSegundos());
        assertEquals("tok-1", dto.getTokenSolicitud());

        RespuestaCodigoSeguridadDTO err = RespuestaCodigoSeguridadDTO.error(400, "Error");
        assertEquals(400, err.getCodigo());
        assertEquals("Error", err.getMensaje());
    }
}

