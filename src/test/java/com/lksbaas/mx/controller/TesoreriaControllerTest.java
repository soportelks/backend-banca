package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.service.IDinamicoService;
import com.lksbaas.mx.service.impl.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TesoreriaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ImplPagosServices pagosServices;

    @Mock
    private ImplPagosMasivosServices pagosServicesMasivos;

    @Mock
    private ComprobantesService comprobantesService;

    @Mock
    private ImplTraspasosServices traspasos;

    @Mock
    private ImplSaldoServices saldo;

    @Mock
    private ImplComicionesServices comiciones;

    @Mock
    private IDinamicoService service;

    @InjectMocks
    private TesoreriaController tesoreriaController;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tesoreriaController).build();
    }

    @Test
    @DisplayName("spsMovHistoricoCliente delega en servicio")
    void testSpsMovHistoricoCliente() throws Exception {
        when(service.execQueryDinamico(any(Request.class), nullable(String.class)))
                .thenReturn("{\"resultado\":\"ok\"}");

        Request req = new Request();
        mockMvc.perform(post("/tesoreria/get-mov-historico-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"resultado\":\"ok\"}"));
    }

    @Test
    @DisplayName("obtenerComisionesPorNumeroCliente delega en comiciones service")
    void testObtenerComisionesPorNumeroCliente() throws Exception {
        Map<String, String> req = Map.of("numero_cliente", "12345");
        when(comiciones.obtenerComisionesPorNumeroCliente("12345"))
                .thenReturn(Map.of("codigo", 0, "mensaje", "ok"));

        mockMvc.perform(post("/tesoreria/comiciones-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
