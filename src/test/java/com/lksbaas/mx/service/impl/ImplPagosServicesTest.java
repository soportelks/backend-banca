package com.lksbaas.mx.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.service.IDinamicoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("registrarSPEI normaliza monto a 2 decimales y espacios antes de firmar y enviar a STP")
    void testRegistrarSPEI_NormalizaAntesDeFirmar() throws Exception {
        ImplPagosServices spyPagos = Mockito.spy(pagosServices);

        when(utils.obtenerFechaOperacion()).thenReturn(Map.of("fechaOperacion", "20261113"));
        when(utils.generarUltimaClaveRastreo()).thenReturn(Map.of("nuevaClave", "646RDAMB2026111300000127"));
        when(saldos.validarSaldo(any())).thenReturn(true);
        doCallRealMethod().when(firma).normalizarPagoStp(any());
        doCallRealMethod().when(firma).generarCadenaFirma(any());
        when(firma.firmarDatos(any())).thenReturn("FIRMA_TEST");
        doReturn("{\"resultado\":{\"descripcionError\":\"Error validando la firma\",\"id\":0}}")
                .when(spyPagos).enviarPagos(any());

        ObjectNode pago = mapper.createObjectNode();
        pago.put("conceptoPago", "pago");
        pago.put("cuentaOrdenante", "646180606500000424");
        pago.put("cuentaBeneficiario", "012028015499143465");
        pago.put("empresa", "CAJA_RDAM");
        pago.put("institucionContraparte", "40012");
        pago.put("institucionOperante", "90646");
        pago.put("monto", "6136.8");
        pago.put("nombreBeneficiario", "Edgar Falcon");
        pago.put("nombreOrdenante", "APC ASESORIA INTEGRAL CONTABLE  - FISCAL");
        pago.put("referenciaNumerica", "1451166");
        pago.put("rfcCurpBeneficiario", "ND");
        pago.put("rfcCurpOrdenante", "ND");
        pago.put("tipoCuentaBeneficiario", "40");
        pago.put("tipoCuentaOrdenante", "40");
        pago.put("tipoPago", "1");

        ObjectNode root = mapper.createObjectNode();
        root.set("pago", pago);
        root.set("cliente", mapper.createObjectNode());

        String respJson = spyPagos.registrarSPEI(root);
        JsonNode resp = mapper.readTree(respJson);

        assertEquals("6136.80", pago.get("monto").asText());
        assertEquals("APC ASESORIA INTEGRAL CONTABLE - FISCAL", pago.get("nombreOrdenante").asText());
        verify(firma).firmarDatos(argThat(cadena ->
                cadena.contains("|6136.80|1|") && cadena.contains("|APC ASESORIA INTEGRAL CONTABLE - FISCAL|")));
        assertEquals("1", resp.get("codigo").asText());
        assertEquals("Error validando la firma", resp.get("mensaje").asText());
        verifyNoInteractions(service);
    }
}

