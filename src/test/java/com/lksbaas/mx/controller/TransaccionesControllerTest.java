package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.model.CrudGenerico;
import com.lksbaas.mx.service.ITransaccionesService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransaccionesControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ITransaccionesService service;

    @InjectMocks
    private TransaccionesController controller;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("crudInversion delega en ITransaccionesService")
    void testCrudInversion() throws Exception {
        when(service.crudInversion(any(CrudGenerico.class)))
                .thenReturn(new String[]{"0", "Operacion exitosa"});

        CrudGenerico crud = new CrudGenerico();
        mockMvc.perform(post("/transaccion/inversion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(crud)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("0"))
                .andExpect(jsonPath("$[1]").value("Operacion exitosa"));
    }
}

