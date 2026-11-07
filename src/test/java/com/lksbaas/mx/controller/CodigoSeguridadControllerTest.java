package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.model.security.RespuestaCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.RespuestaValidarCodigoDTO;
import com.lksbaas.mx.model.security.SolicitudCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.SolicitudValidarCodigoDTO;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import com.lksbaas.mx.service.security.ICodigoSeguridadService;
import com.lksbaas.mx.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CodigoSeguridadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ICodigoSeguridadService codigoSeguridadService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private CodigoSeguridadController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("5512345678", null, Collections.emptyList())
        );
    }

    @Test
    @DisplayName("POST /seguridad/codigo/solicitar: Retorna 200 con DTO estructurado")
    void testSolicitarCodigo_Http200() throws Exception {
        SolicitudCodigoSeguridadDTO solicitud = new SolicitudCodigoSeguridadDTO("TRANSFERENCIA_SPEI");

        RespuestaCodigoSeguridadDTO respuestaDTO = RespuestaCodigoSeguridadDTO.exito(
                "Código de seguridad enviado exitosamente",
                "c***o@dominio.com",
                "TRANSFERENCIA_SPEI",
                120,
                "token-uuid-123"
        );

        when(codigoSeguridadService.solicitarCodigo(eq("5512345678"), any(SolicitudCodigoSeguridadDTO.class)))
                .thenReturn(respuestaDTO);

        mockMvc.perform(post("/seguridad/codigo/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.destinatarioEnmascarado").value("c***o@dominio.com"))
                .andExpect(jsonPath("$.tipoTransaccion").value("TRANSFERENCIA_SPEI"))
                .andExpect(jsonPath("$.tokenSolicitud").value("token-uuid-123"));
    }

    @Test
    @DisplayName("POST /seguridad/codigo/validar: Valida correctamente y retorna token de autorización")
    void testValidarCodigo_Http200() throws Exception {
        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", "123456", "token-uuid-123");

        RespuestaValidarCodigoDTO respuestaDTO = RespuestaValidarCodigoDTO.exito(
                "Código validado exitosamente",
                "auth-ticket-xyz",
                3
        );

        when(codigoSeguridadService.validarCodigo(eq("5512345678"), any(SolicitudValidarCodigoDTO.class)))
                .thenReturn(respuestaDTO);

        mockMvc.perform(post("/seguridad/codigo/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.tokenAutorizacion").value("auth-ticket-xyz"));
    }

    @Test
    @DisplayName("POST /seguridad/codigo/verificar-token: Verifica existencia de token de autorización")
    void testVerificarToken_Http200() throws Exception {
        when(codigoSeguridadService.validarTokenAutorizacion("5512345678", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, "auth-ticket-xyz"))
                .thenReturn(true);

        Map<String, String> body = Map.of(
                "tipoTransaccion", "TRANSFERENCIA_SPEI",
                "tokenAutorizacion", "auth-ticket-xyz"
        );

        mockMvc.perform(post("/seguridad/codigo/verificar-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true));
    }
}

