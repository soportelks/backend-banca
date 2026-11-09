package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.service.IDinamicoService;
import com.lksbaas.mx.service.impl.ImplUtilsServices;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogosControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IDinamicoService serviceCatalogo;

    @Mock
    private ImplUtilsServices utilsServices;

    @InjectMocks
    private CatalogosController controller;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("getFechaOperacion retorna 200 y maneja excepcion 500")
    void testFechaOperacion() throws Exception {
        when(utilsServices.obtenerFechaOperacion()).thenReturn(Map.of("fecha", "2026-11-08"));
        mockMvc.perform(get("/catalogo/fecha-operacion"))
                .andExpect(status().isOk());

        when(utilsServices.obtenerFechaOperacion()).thenThrow(new RuntimeException("Error"));
        mockMvc.perform(get("/catalogo/fecha-operacion"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("getUltimaClaveRastreo retorna 200 y maneja excepcion 500")
    void testUltimaClaveRastreo() throws Exception {
        when(utilsServices.generarUltimaClaveRastreo()).thenReturn(Map.of("clave", "CR123"));
        mockMvc.perform(get("/catalogo/ultima-clave-rastreo"))
                .andExpect(status().isOk());

        when(utilsServices.generarUltimaClaveRastreo()).thenThrow(new RuntimeException("Error"));
        mockMvc.perform(get("/catalogo/ultima-clave-rastreo"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("spsGeneralesCategoria y spsProductosServicios retornan resultado dinamico")
    void testConsultasDinamicas() throws Exception {
        when(serviceCatalogo.execQueryDinamico(any(), any())).thenReturn("{\"codigo\":0}");

        Request request = new Request();
        mockMvc.perform(post("/catalogo/get-generales-categoria")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/catalogo/get-productos-servicios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}

