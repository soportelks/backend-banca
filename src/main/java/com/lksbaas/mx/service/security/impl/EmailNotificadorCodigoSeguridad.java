package com.lksbaas.mx.service.security.impl;

import com.lksbaas.mx.config.SeguridadCodigoProperties;
import com.lksbaas.mx.exception.CodigoSeguridadException;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import com.lksbaas.mx.service.security.INotificadorCodigoSeguridad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Implementación de canal de notificación por correo electrónico.
 * Formatea un correo corporativo de alta seguridad para la autorización de transacciones financieras.
 */
@Service
public class EmailNotificadorCodigoSeguridad implements INotificadorCodigoSeguridad {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificadorCodigoSeguridad.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remitente;

    @Override
    public void enviarCodigo(String destinatario, String codigo,
                             TipoTransaccionSeguridad tipoTransaccion,
                             int minutosExpiracion, boolean esEntornoPrueba) {
        if (destinatario == null || destinatario.trim().isEmpty()) {
            throw new CodigoSeguridadException("No se ha proporcionado una dirección de correo válida para el envío");
        }

        String destinoNormalizado = destinatario.trim();
        String enmascarado = SeguridadCodigoProperties.enmascararCorreo(destinoNormalizado);
        log.info("Despachando código de seguridad transaccional ({}) hacia destinatario: {}",
                tipoTransaccion.name(), enmascarado);

        int maxRetries = 2;
        int attempt = 0;
        Exception lastError = null;

        while (attempt <= maxRetries) {
            try {
                doEnviarEmail(destinoNormalizado, enmascarado, codigo, tipoTransaccion, minutosExpiracion, esEntornoPrueba);
                log.info("Código de seguridad enviado exitosamente al correo: {}", enmascarado);
                return;
            } catch (Exception e) {
                attempt++;
                lastError = e;
                log.warn("Intento {} fallido al enviar correo a {}: {}", attempt, enmascarado, e.getMessage());
                if (attempt <= maxRetries) {
                    try {
                        Thread.sleep(800L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        log.error("Fallo al enviar correo con código de seguridad a {} tras {} intentos: {}",
                enmascarado, maxRetries + 1, lastError != null ? lastError.getMessage() : "Desconocido");
        throw new CodigoSeguridadException("No fue posible enviar el código de seguridad al correo configurado: "
                + (lastError != null ? lastError.getMessage() : "Error de conexión SMTP"), lastError);
    }

    private void doEnviarEmail(String destinoNormalizado, String enmascarado, String codigo,
                               TipoTransaccionSeguridad tipoTransaccion, int minutosExpiracion,
                               boolean esEntornoPrueba) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(remitente);
        helper.setTo(destinoNormalizado);
        helper.setSubject("Código de Verificación");

        String horaEnvio = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String contentId = "logoImage";

        String htmlBody = "<div style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>"
                + "<div style='max-width: 600px; margin: 0 auto;'>"

                // Encabezado azul con imagen
                + "<div style='background-color: #2b3751; padding: 20px; border-radius: 10px 10px 0 0; text-align: center;'>"
                + "<img src='cid:" + contentId + "' alt='Logo' style='width: 150px; height: auto;'/>"
                + "<h1 style='color: white; margin: 10px 0 0 0; font-size: 24px;'>Bienvenido a la Banca Web</h1>"
                + "</div>"

                // Contenido principal
                + "<div style='background-color: #ffffff; padding: 30px; text-align: center;'>"
                + "<h2 style='color: #333; font-size: 26px;'>¡Hola!</h2>"
                + "<p style='font-size: 18px; color: #555;'>Tu código de verificación es:</p>"
                + "<div style='font-size: 36px; font-weight: bold; color: #007BFF; margin: 20px 0;'>" + codigo
                + "</div>"
                + "<p style='font-size: 16px; color: #777;'>Este código expirará en <strong>" + minutosExpiracion + " minutos</strong>.</p>"
                + "</div>"

                // Pie azul
                + "<div style='background-color: #2b3751; padding: 15px; border-radius: 0 0 10px 10px; text-align: center;'>"
                + "<p style='color: white; font-size: 14px; margin: 0;'>Hora de envío: " + horaEnvio + "</p>"
                + "</div>"

                + "</div></div>";

        helper.setText(htmlBody, true);

        // Adjuntar el logo inline si existe en classpath
        try {
            ClassPathResource resource = new ClassPathResource("images/Logo_RDAM_blanco.png");
            if (resource.exists()) {
                helper.addInline(contentId, resource, "image/png");
            }
        } catch (Exception ex) {
            log.warn("No se pudo incrustar el recurso de imagen inline en el correo: {}", ex.getMessage());
        }

        mailSender.send(message);
    }
}


