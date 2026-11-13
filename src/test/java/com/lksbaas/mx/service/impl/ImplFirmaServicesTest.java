package com.lksbaas.mx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.exception.ErrorOperacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplFirmaServicesTest {

    private final ImplFirmaServices firmaServices = new ImplFirmaServices();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("firmarDatos con emulación STP activa retorna firma mock válida")
    void testFirmarDatos_Emulacion() {
        ReflectionTestUtils.setField(firmaServices, "stpEmulacionEnabled", true);

        String firma = firmaServices.firmarDatos("cadena-a-firmar");
        assertNotNull(firma);
        assertTrue(firma.length() > 0);
    }

    @Test
    @DisplayName("firmarDatos con pem inexistente lanza ErrorOperacionException")
    void testFirmarDatos_Error() {
        ReflectionTestUtils.setField(firmaServices, "stpEmulacionEnabled", false);
        assertThrows(
                ErrorOperacionException.class,
                () -> firmaServices.firmarDatos("cadena-a-firmar")
        );
    }

    @Test
    @DisplayName("generarCadenaFirma construye la cadena requerida para STP")
    void testGenerarCadenaFirma() {
        ObjectNode node = crearOrdenBase();

        String cadena = firmaServices.generarCadenaFirma(node);
        assertNotNull(cadena);
        assertTrue(cadena.contains("CR123"));
        assertTrue(cadena.contains("CAJA_RDAM"));
        assertTrue(cadena.contains("|100.50|1|"));
    }

    @Test
    @DisplayName("generarCadenaFirma y normalizarPagoStp rellenan monto a dos decimales y limpian espacios múltiples")
    void testNormalizarYGenerarCadenaFirma_CasoProduccion() {
        ObjectNode node = mapper.createObjectNode();
        node.put("claveRastreo", "646RDAMB2026111300000127");
        node.put("institucionContraparte", "40012");
        node.put("empresa", "CAJA_RDAM");
        node.put("monto", "6136.8");
        node.put("tipoCuentaOrdenante", "40");
        node.put("nombreOrdenante", "APC ASESORIA INTEGRAL CONTABLE  - FISCAL");
        node.put("cuentaOrdenante", "646180606500000424");
        node.put("rfcCurpOrdenante", "ND");
        node.put("tipoCuentaBeneficiario", "40");
        node.put("nombreBeneficiario", "Edgar   Falcon");
        node.put("cuentaBeneficiario", "012028015499143465");
        node.put("rfcCurpBeneficiario", "ND");
        node.put("conceptoPago", "pago");
        node.put("referenciaNumerica", "1451166");
        node.put("institucionOperante", "90646");
        node.put("tipoPago", "1");

        firmaServices.normalizarPagoStp(node);

        assertEquals("6136.80", node.get("monto").asText());
        assertEquals("APC ASESORIA INTEGRAL CONTABLE - FISCAL", node.get("nombreOrdenante").asText());
        assertEquals("Edgar Falcon", node.get("nombreBeneficiario").asText());

        String cadenaEsperada = "||40012|CAJA_RDAM|||646RDAMB2026111300000127|90646|6136.80|1|40|"
                + "APC ASESORIA INTEGRAL CONTABLE - FISCAL|646180606500000424|ND|40|"
                + "Edgar Falcon|012028015499143465|ND||||||pago||||||1451166||||||||";

        assertEquals(cadenaEsperada, firmaServices.generarCadenaFirma(node));
    }

    @Test
    @DisplayName("formatearMonto formatea enteros, un decimal y montos numéricos")
    void testFormatearMonto() {
        assertEquals("6136.80", ImplFirmaServices.formatearMonto("6136.8"));
        assertEquals("6136.00", ImplFirmaServices.formatearMonto("6136"));
        assertEquals("0.50", ImplFirmaServices.formatearMonto(" .5 "));
        assertEquals("10000000.00", ImplFirmaServices.formatearMonto("10000000"));
        assertThrows(ErrorOperacionException.class, () -> ImplFirmaServices.formatearMonto(""));
        assertThrows(ErrorOperacionException.class, () -> ImplFirmaServices.formatearMonto("abc"));
    }

    @Test
    @DisplayName("generarCadenaFirma respeta tipoPago explícito y falla si falta campo obligatorio")
    void testGenerarCadenaFirma_TipoPagoYCampoFaltante() {
        ObjectNode node = crearOrdenBase();
        node.put("tipoPago", "2");
        assertTrue(firmaServices.generarCadenaFirma(node).contains("|100.50|2|"));

        node.remove("monto");
        assertThrows(ErrorOperacionException.class, () -> firmaServices.generarCadenaFirma(node));
    }

    private ObjectNode crearOrdenBase() {
        ObjectNode node = mapper.createObjectNode();
        node.put("claveRastreo", "CR123");
        node.put("institucionContraparte", "90646");
        node.put("empresa", "CAJA_RDAM");
        node.put("monto", "100.50");
        node.put("tipoCuentaOrdenante", "40");
        node.put("nombreOrdenante", "Juan Perez");
        node.put("cuentaOrdenante", "646180606500000000");
        node.put("rfcCurpOrdenante", "PEPJ800101");
        node.put("tipoCuentaBeneficiario", "40");
        node.put("nombreBeneficiario", "Maria Lopez");
        node.put("cuentaBeneficiario", "646180606511111111");
        node.put("rfcCurpBeneficiario", "LOPM850202");
        node.put("conceptoPago", "Pago de prueba");
        node.put("referenciaNumerica", "1234567");
        node.put("institucionOperante", "90646");
        return node;
    }
}
