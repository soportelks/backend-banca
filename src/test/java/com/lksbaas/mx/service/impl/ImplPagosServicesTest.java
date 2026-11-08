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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ImplPagosServicesTest {

    @Mock
    private IDinamicoService service;

    @Mock
    private ImplUtilsServices utils;

    @Mock
    private ImplSaldoServices saldos;

    @Mock
    private ImplFirmaServices firma;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ImplComicionesServices comiciones;

    @InjectMocks
    private ImplPagosServices pagosServices;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("enviarPagos en modo emulado STP retorna respuesta emulada valida")
    void testEnviarPagos_Emulado() {
        ReflectionTestUtils.setField(pagosServices, "stpEmulacionEnabled", true);

        ObjectNode json = mapper.createObjectNode();
        json.put("monto", "100.00");

        String resp = pagosServices.enviarPagos(json);
        assertNotNull(resp);
        assertTrue(resp.contains("MOCK"));
    }
}

