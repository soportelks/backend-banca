package com.lksbaas.mx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.service.IDinamicoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImplSaldoServicesTest {

    @Mock
    private IDinamicoService dinamicoService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ImplSaldoServices saldoServices;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(saldoServices, "urlSaldos", "http://test-saldo.com");
        ReflectionTestUtils.setField(saldoServices, "querySaldoAhorroCliente", "SELECT * FROM sp()");
    }

    @Test
    @DisplayName("validarSaldo retorna true cuando saldo es suficiente")
    void testValidarSaldo_Suficiente() {
        ObjectNode root = mapper.createObjectNode();
        root.put("accion", 1);
        ObjectNode datos = root.putObject("datos");
        datos.put("montoConComicion", "100.00");

        String spJson = "{\"codigo\":\"0\",\"info\":{\"saldo\":\"150.00\"}}";
        when(jdbcTemplate.queryForMap(anyString(), anyString(), anyInt()))
                .thenReturn(Map.of("sps_saldomovimientobycliente_json", spJson));

        boolean resultado = saldoServices.validarSaldo(root);
        assertTrue(resultado);
    }

    @Test
    @DisplayName("validarSaldo retorna false cuando codigo no es 0 o saldo es insuficiente")
    void testValidarSaldo_Insuficiente() {
        ObjectNode root = mapper.createObjectNode();
        root.put("accion", 1);
        ObjectNode datos = root.putObject("datos");
        datos.put("montoConComicion", "500.00");

        String spJson = "{\"codigo\":\"0\",\"info\":{\"saldo\":\"150.00\"}}";
        when(jdbcTemplate.queryForMap(anyString(), anyString(), anyInt()))
                .thenReturn(Map.of("sps_saldomovimientobycliente_json", spJson));

        boolean resultado = saldoServices.validarSaldo(root);
        assertFalse(resultado);
    }

    @Test
    @DisplayName("validarSaldoAhorro con saldo suficiente y monto menor")
    void testValidarSaldoAhorro_Exito() {
        Request req = new Request();
        req.setDatos("{\"monto\": 200.50, \"id\": 1}");

        when(dinamicoService.execQueryDinamico(any(Request.class), anyString()))
                .thenReturn("{\"codigo\" : \"0\", \"saldo\": 500.00}");

        Boolean res = saldoServices.validarSaldoAhorro(req);
        assertTrue(res);
    }

    @Test
    @DisplayName("validarSaldoAhorro con monto mayor al saldo")
    void testValidarSaldoAhorro_Insuficiente() {
        Request req = new Request();
        req.setDatos("{\"monto\": 1000.00}");

        when(dinamicoService.execQueryDinamico(any(Request.class), anyString()))
                .thenReturn("{\"codigo\" : \"0\", \"saldo\": 500.00}");

        Boolean res = saldoServices.validarSaldoAhorro(req);
        assertFalse(res);
    }
}

