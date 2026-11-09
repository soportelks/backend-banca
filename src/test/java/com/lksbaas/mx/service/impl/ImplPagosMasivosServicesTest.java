package com.lksbaas.mx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.service.IDinamicoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ImplPagosMasivosServicesTest {

    @Mock
    private IDinamicoService service;

    @Mock
    private ImplUtilsServices utils;

    @Mock
    private ImplFirmaServices firma;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ImplPagosMasivosServices pagosMasivosServices;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("quitarComillasExtremos elimina comillas en extremos respetando cadenas vacias y comillas individuales")
    void testQuitarComillasExtremos() {
        assertNull(ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", (Object) null));
        assertEquals("", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", ""));
        assertEquals("", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "\""));
        assertEquals("", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "\"\""));
        assertEquals("test", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "\"test\""));
        assertEquals("test", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "test"));
        assertEquals("test", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "\"test"));
        assertEquals("test", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "test\""));
        assertEquals("te\"st", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "\"te\"st\""));
        assertEquals("\"", ReflectionTestUtils.invokeMethod(ImplPagosMasivosServices.class, "quitarComillasExtremos", "\"\"\""));
    }

    @Test
    @DisplayName("enviarPagos en modo emulado STP retorna respuesta emulada valida")
    void testEnviarPagos_Emulado() {
        ReflectionTestUtils.setField(pagosMasivosServices, "stpEmulacionEnabled", true);

        ObjectNode json = mapper.createObjectNode();
        json.put("monto", "500.00");

        String resp = pagosMasivosServices.enviarPagos(json);
        assertNotNull(resp);
        assertTrue(resp.contains("MOCK"));
    }
}
