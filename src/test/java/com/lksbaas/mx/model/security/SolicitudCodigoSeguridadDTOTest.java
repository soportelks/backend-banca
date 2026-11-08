package com.lksbaas.mx.model.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolicitudCodigoSeguridadDTOTest {

    @Test
    @DisplayName("SolicitudCodigoSeguridadDTO constructores, getters y setters")
    void testDTO() {
        SolicitudCodigoSeguridadDTO dto1 = new SolicitudCodigoSeguridadDTO();
        dto1.setTipoTransaccion("TRANSFERENCIA_SPEI");
        dto1.setCorreoAlternativo("alt@banca.com");
        dto1.setMetadatos(Map.of("monto", 500));

        assertEquals("TRANSFERENCIA_SPEI", dto1.getTipoTransaccion());
        assertEquals("alt@banca.com", dto1.getCorreoAlternativo());
        assertEquals(500, dto1.getMetadatos().get("monto"));

        SolicitudCodigoSeguridadDTO dto2 = new SolicitudCodigoSeguridadDTO("TRASPASO");
        assertEquals("TRASPASO", dto2.getTipoTransaccion());

        SolicitudCodigoSeguridadDTO dto3 = new SolicitudCodigoSeguridadDTO("PAGO", "test@test.com");
        assertEquals("PAGO", dto3.getTipoTransaccion());
        assertEquals("test@test.com", dto3.getCorreoAlternativo());
    }
}

