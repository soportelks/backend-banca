package com.lksbaas.mx.service.security.impl;

import com.lksbaas.mx.exception.CodigoSeguridadException;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificadorCodigoSeguridadTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailNotificadorCodigoSeguridad notificador;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificador, "remitente", "noreply@banca.com");
    }

    @Test
    @DisplayName("enviarCodigo: Valida que el correo no sea nulo o vacío")
    void testEnviarCodigo_DestinatarioInvalido() {
        assertThrows(CodigoSeguridadException.class, () ->
                notificador.enviarCodigo(null, "123456", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, 2, false)
        );
        assertThrows(CodigoSeguridadException.class, () ->
                notificador.enviarCodigo("   ", "123456", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, 2, false)
        );
    }

    @Test
    @DisplayName("enviarCodigo: Envia correo satisfactoriamente")
    void testEnviarCodigo_Exito() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        notificador.enviarCodigo("destinatario@banca.com", "654321", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, 2, false);

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("enviarCodigo: Maneja reintentos y lanza CodigoSeguridadException en fallo de envio")
    void testEnviarCodigo_FalloReintentos() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new org.springframework.mail.MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(CodigoSeguridadException.class, () ->
                notificador.enviarCodigo("destinatario@banca.com", "654321", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, 2, false)
        );
    }
}
