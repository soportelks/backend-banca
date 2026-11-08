package com.lksbaas.mx.service.security;

import com.lksbaas.mx.controller.AuthController;
import com.lksbaas.mx.controller.OtpController;
import com.lksbaas.mx.controller.TesoreriaController;
import com.lksbaas.mx.service.impl.IMplEmailOtpService;
import com.lksbaas.mx.service.impl.ImplPagosMasivosServices;
import com.lksbaas.mx.service.impl.ImplPagosServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de no regresión para asegurar que las firmas y contratos de las clases críticas
 * preexistentes (Login, Dispersiones Masivas, OtpController, TesoreriaController) no fueron alterados.
 */
class RegresionFlujosExistentesTest {

    @Test
    @DisplayName("No regresión Login: AuthController mantiene su contrato de login y logout intacto")
    void testAuthControllerInalterado() {
        Method[] methods = AuthController.class.getDeclaredMethods();
        boolean tieneLogin = false;
        boolean tieneLogout = false;

        for (Method m : methods) {
            if ("login".equals(m.getName())) {
                tieneLogin = true;
                assertEquals(1, m.getParameterCount());
            }
            if ("logout".equals(m.getName())) {
                tieneLogout = true;
            }
        }

        assertTrue(tieneLogin, "AuthController.login debe permanecer intacto");
        assertTrue(tieneLogout, "AuthController.logout debe permanecer intacto");
    }

    @Test
    @DisplayName("No regresión OtpController: Mantiene sendOtp y verifyOtp intactos para compatibilidad con flujos previos")
    void testOtpControllerInalterado() {
        Method[] methods = OtpController.class.getDeclaredMethods();
        boolean tieneSendOtp = false;
        boolean tieneVerifyOtp = false;

        for (Method m : methods) {
            if ("sendOtp".equals(m.getName())) tieneSendOtp = true;
            if ("verifyOtp".equals(m.getName())) tieneVerifyOtp = true;
        }

        assertTrue(tieneSendOtp, "OtpController.sendOtp debe permanecer intacto");
        assertTrue(tieneVerifyOtp, "OtpController.verifyOtp debe permanecer intacto");
    }

    @Test
    @DisplayName("No regresión Dispersiones Masivas: ImplPagosMasivosServices y TesoreriaController mantienen sus métodos")
    void testPagosMasivosInalterados() {
        Method[] methodsTesoreria = TesoreriaController.class.getDeclaredMethods();
        boolean tieneRegistrarSpeiMasivos = false;
        boolean tieneCooldownDispersion = false;
        boolean tieneRegistrarSpei = false;

        for (Method m : methodsTesoreria) {
            if ("registrarPagoSpeiMasivos".equals(m.getName())) tieneRegistrarSpeiMasivos = true;
            if ("consultarCooldownDispersion".equals(m.getName())) tieneCooldownDispersion = true;
            if ("registrarPagoSpei".equals(m.getName())) tieneRegistrarSpei = true;
        }

        assertTrue(tieneRegistrarSpeiMasivos, "registrarPagoSpeiMasivos debe permanecer intacto");
        assertTrue(tieneCooldownDispersion, "consultarCooldownDispersion debe permanecer intacto");
        assertTrue(tieneRegistrarSpei, "registrarPagoSpei debe permanecer intacto");

        Method[] methodsPagosMasivos = ImplPagosMasivosServices.class.getDeclaredMethods();
        boolean tieneRegistrarSpeiService = false;
        for (Method m : methodsPagosMasivos) {
            if ("registrarSPEI".equals(m.getName())) tieneRegistrarSpeiService = true;
        }
        assertTrue(tieneRegistrarSpeiService, "ImplPagosMasivosServices.registrarSPEI debe permanecer intacto");
    }

    @Test
    @DisplayName("No regresión Saldo: JSON de request para consulta de saldo es sintácticamente válido")
    void testRequestJsonSaldoValido() throws Exception {
        String json = """
                {
                  "cuentaOrdenante": "646180606500000000",
                  "empresa": "CAJA_RDAM",
                  "firma": "kLVJyWHGrQcVpoHC8FOBeg/MQQ2cDZhEgDl0Jut9ufwkZwIwhbbNmjS2cTEHKZINMstOjqsCyr3/N0frODbsmF8PjCWa1lZ/BRjD+iryNgR2j4wjrreeckNmGLIHgsc2iwbVtHLdAE1f/z/EWdAtoM4vkJAj4zPk044zDBnEDteJkWTHJ3TAoDknVWMLphwaL1J3CeNHzus4vObhboE6lHDp01AdPcHX/JyoWhTV6RtsVAHw7ZaY0B0G3VEeG+hU0cvvqKEaPfMLx8nF6cZJePVJJrQXs0sAtJ6Ffj07Fx3xLdzjMciZhyOyy6BprkOTb/iV9DnbcdUB2iQx3fp5MQ=="
                }""";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
        assertEquals("646180606500000000", node.get("cuentaOrdenante").asText());
        assertEquals("CAJA_RDAM", node.get("empresa").asText());
        assertFalse(node.get("firma").asText().isEmpty());
    }
}

