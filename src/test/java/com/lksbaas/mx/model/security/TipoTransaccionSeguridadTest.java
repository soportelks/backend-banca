package com.lksbaas.mx.model.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TipoTransaccionSeguridadTest {

    @Test
    @DisplayName("desdeCadena resuelve cadenas y maneja nulos o valores invalidos")
    void testDesdeCadena() {
        assertEquals(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, TipoTransaccionSeguridad.desdeCadena(null));
        assertEquals(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, TipoTransaccionSeguridad.desdeCadena("   "));
        assertEquals(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, TipoTransaccionSeguridad.desdeCadena("transferencia-spei"));
        assertEquals(TipoTransaccionSeguridad.TRASPASO, TipoTransaccionSeguridad.desdeCadena("traspaso"));
        assertEquals(TipoTransaccionSeguridad.CAMBIO_CONTRASENA, TipoTransaccionSeguridad.desdeCadena("CAMBIO_CONTRASENA"));

        assertNotNull(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI.getDescripcion());

        assertThrows(IllegalArgumentException.class, () -> TipoTransaccionSeguridad.desdeCadena("OPERACION_DESCONOCIDA"));
    }
}

