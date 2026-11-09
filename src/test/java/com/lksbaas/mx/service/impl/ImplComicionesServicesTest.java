package com.lksbaas.mx.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImplComicionesServicesTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ImplComicionesServices comisionesServices;

    @Test
    @DisplayName("procesarMovimientoComision con datos faltantes retorna error")
    void testProcesarMovimientoComision_FaltanDatos() {
        Map<String, Object> req = Map.of("cveCliente", "CLI01");
        Map<String, Object> resp = comisionesServices.procesarMovimientoComision(req);
        assertTrue(resp.containsKey("error"));
    }

    @Test
    @DisplayName("procesarMovimientoComision con datos validos")
    void testProcesarMovimientoComision_Exito() {
        List<Object> fila = List.of("100.00", "DEBITO");
        Map<String, Object> req = Map.of(
                "cveCliente", "CLI01",
                "usuario", "admin",
                "cveSucursal", "SUC1",
                "cveCaja", "CAJA1",
                "movimientos", List.of(fila)
        );

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any(), any(), any(), any()))
                .thenReturn("[0:5]={RES123}");

        Map<String, Object> resp = comisionesServices.procesarMovimientoComision(req);
        assertNotNull(resp);
        assertEquals("RES123", resp.get("resultado"));
    }

    @Test
    @DisplayName("obtenerComisionesPorNumeroCliente vacio retorna default")
    void testObtenerComisiones_Vacio() {
        when(jdbcTemplate.queryForList(anyString(), eq("12345")))
                .thenReturn(Collections.emptyList());

        Map<String, Object> resp = comisionesServices.obtenerComisionesPorNumeroCliente("12345");
        assertEquals(0, resp.get("codigo"));
        assertNotNull(resp.get("info"));
    }

    @Test
    @DisplayName("obtenerComisionesPorNumeroCliente con resultado")
    void testObtenerComisiones_ConResultado() {
        Map<String, Object> fila = Map.of("id", 1, "comision_spei", "5.00");
        when(jdbcTemplate.queryForList(anyString(), eq("12345")))
                .thenReturn(List.of(fila));

        Map<String, Object> resp = comisionesServices.obtenerComisionesPorNumeroCliente("12345");
        assertEquals(0, resp.get("codigo"));
        assertEquals(1, ((List<?>) resp.get("info")).size());
    }
}

