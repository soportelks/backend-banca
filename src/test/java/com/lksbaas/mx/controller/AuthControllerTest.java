package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.model.Prospecto;
import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProspectoService prospectoService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("login con parametros faltantes retorna 400")
    void testLogin_FaltanDatos() throws Exception {
        Map<String, String> req = Map.of("numero_telefono", "5512345678");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1));
    }

    @Test
    @DisplayName("login con credenciales invalidas retorna 401")
    void testLogin_CredencialesInvalidas() throws Exception {
        when(prospectoService.getProspecto("5512345678")).thenReturn(null);

        Map<String, String> req = Map.of("numero_telefono", "5512345678", "contrasena", "badpass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(1));
    }

    @Test
    @DisplayName("login exitoso retorna 200 y token")
    void testLogin_Exito() throws Exception {
        Prospecto p = new Prospecto();
        p.setNumeroTelefono("5512345678");
        p.setClaveCliente("CLI01");

        when(prospectoService.getProspecto("5512345678")).thenReturn(p);
        when(prospectoService.validarCredenciales("5512345678", "goodpass")).thenReturn(true);
        when(prospectoService.validadEstado(any())).thenReturn(Map.of("codigo", 0));
        when(jwtUtil.generateToken(any(), anyString())).thenReturn("mock-jwt-token");

        Map<String, String> req = Map.of("numero_telefono", "5512345678", "contrasena", "goodpass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.info.token").value("mock-jwt-token"));
    }
}
