package com.lksbaas.mx.service;

import com.lksbaas.mx.util.AESUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProspectoServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AESUtil aesUtil;

    @InjectMocks
    private ProspectoService prospectoService;

    @Test
    @DisplayName("verificarContrasena retorna true cuando la contrasena coincide")
    void testVerificarContrasena_Coincide() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1)))
                .thenReturn("cifrado123");
        when(aesUtil.descifrar("cifrado123")).thenReturn("passwordPlano");

        assertTrue(prospectoService.verificarContrasena(1, "passwordPlano"));
        assertFalse(prospectoService.verificarContrasena(1, "otraPassword"));
    }

    @Test
    @DisplayName("verificarContrasena maneja EmptyResultDataAccessException y excepciones")
    void testVerificarContrasena_Excepciones() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(99)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertFalse(prospectoService.verificarContrasena(99, "pass"));
    }

    @Test
    @DisplayName("cambiarContrasena actualiza correctamente cuando la actual es valida")
    void testCambiarContrasena_Valida() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1)))
                .thenReturn("cifrado123");
        when(aesUtil.descifrar("cifrado123")).thenReturn("actualPass");
        when(aesUtil.cifrar("nuevaPass")).thenReturn("nuevoCifrado");

        Map<String, Object> resp = prospectoService.cambiarContrasena(1, "nuevaPass", "actualPass");
        assertEquals(0, resp.get("codigo"));
    }

    @Test
    @DisplayName("cambiarContrasena retorna error cuando la contrasena actual no coincide")
    void testCambiarContrasena_Invalida() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1)))
                .thenReturn("cifrado123");
        when(aesUtil.descifrar("cifrado123")).thenReturn("otraPass");

        Map<String, Object> resp = prospectoService.cambiarContrasena(1, "nuevaPass", "actualPass");
        assertEquals(1, resp.get("codigo"));
    }

    @Test
    @DisplayName("cambiarContrasenaRestauracion actualiza correctamente")
    void testCambiarContrasenaRestauracion() {
        when(aesUtil.cifrar("nuevaPass")).thenReturn("nuevoCifrado");

        Map<String, Object> resp = prospectoService.cambiarContrasenaRestauracion(1, "nuevaPass");
        assertEquals(0, resp.get("codigo"));
    }
}

