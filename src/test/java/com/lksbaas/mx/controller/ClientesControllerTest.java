package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;
import com.lksbaas.mx.service.ProspectoService;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClientesControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProspectoService prospectoService;

    @Mock
    private IDinamicoService dinamicoService;

    @InjectMocks
    private ClientesController controller;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("cambiar-contrasena invoca prospectoService.cambiarContrasena")
    void testCambiarContrasena() throws Exception {
        when(prospectoService.cambiarContrasena(1, "nuevaPass", "actualPass"))
                .thenReturn(Map.of("codigo", 0, "mensaje", "contraseña actualizada correctamente"));

        Map<String, Object> body = Map.of(
                "prospecto_id", 1,
                "contrasena", "nuevaPass",
                "contrasenaActual", "actualPass"
        );

        mockMvc.perform(post("/onboarding/update-contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));
    }

    @Test
    @DisplayName("restaurar-contacto invoca prospectoService.cambiarContrasenaRestauracion")
    void testRestaurarContacto() throws Exception {
        when(prospectoService.cambiarContrasenaRestauracion(1, "nuevaPass"))
                .thenReturn(Map.of("codigo", 0, "mensaje", "contraseña actualizada correctamente"));

        Map<String, Object> body = Map.of(
                "prospecto_id", 1,
                "contrasena", "nuevaPass"
        );

        mockMvc.perform(post("/onboarding/restaurar-contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));
    }

    @Test
    @DisplayName("cifrar y descifrar contrasena")
    void testCifrarYDescifrarContrasena() throws Exception {
        when(prospectoService.encriptarContrasena("pass123"))
                .thenReturn(Map.of("codigo", 0, "resultado", "cifrado"));
        when(prospectoService.desencriptarContrasena("cifrado"))
                .thenReturn(Map.of("codigo", 0, "resultado", "pass123"));

        mockMvc.perform(post("/onboarding/cifrar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("contrasena", "pass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("cifrado"));

        mockMvc.perform(post("/onboarding/descifrar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("contrasena", "cifrado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("pass123"));
    }

    @Test
    @DisplayName("endpoints dinamicos delegan a IDinamicoService")
    void testEndpointsDinamicos() throws Exception {
        when(dinamicoService.execQueryDinamico(any(Request.class), nullable(String.class)))
                .thenReturn("{\"status\":\"ok\"}");
        when(dinamicoService.execQueryDinamico1Param(any(RequestParam.class), nullable(String.class)))
                .thenReturn("{\"status\":\"ok\"}");

        Request req = new Request();
        String json = mapper.writeValueAsString(req);

        mockMvc.perform(post("/onboarding/get-prospectos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(content().string("{\"status\":\"ok\"}"));

        mockMvc.perform(post("/onboarding/get-solicitudes-onboarding").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/crud-prospectos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/revision-solicitudes-onboarding").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/crud-documento-json").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/login-clientes").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/get-montos-inversion").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/calcular-inversiones").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/onboarding/check-sesion").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RequestParam())))
                .andExpect(status().isOk());
    }
}
