package com.lksbaas.mx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.service.impl.IMplEmailOtpService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OtpControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IMplEmailOtpService emailOtpService;

    @InjectMocks
    private OtpController otpController;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(otpController).build();
    }

    @Test
    @DisplayName("sendOtp sin correo retorna 400")
    void testSendOtp_SinCorreo() throws Exception {
        mockMvc.perform(post("/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sendOtp con correo valido retorna 200")
    void testSendOtp_Exito() throws Exception {
        mockMvc.perform(post("/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "test@banca.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@banca.com"));

        verify(emailOtpService).sendOtp("test@banca.com");
    }

    @Test
    @DisplayName("verifyOtp con datos validos")
    void testVerifyOtp_Exito() throws Exception {
        when(emailOtpService.verifyOtp("test@banca.com", 123456)).thenReturn(true);

        mockMvc.perform(post("/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "test@banca.com", "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}

