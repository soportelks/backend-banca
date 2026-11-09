package com.lksbaas.mx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        org.junit.jupiter.api.Assertions.assertThrows(
                com.lksbaas.mx.exception.ErrorOperacionException.class,
                () -> firmaServices.firmarDatos("cadena-a-firmar")
        );
    }

    @Test
    @DisplayName("generarCadenaFirma construye la cadena requerida para STP")
    void testGenerarCadenaFirma() {
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

        String cadena = firmaServices.generarCadenaFirma(node);
        assertNotNull(cadena);
        assertTrue(cadena.contains("CR123"));
        assertTrue(cadena.contains("CAJA_RDAM"));
    }
}
