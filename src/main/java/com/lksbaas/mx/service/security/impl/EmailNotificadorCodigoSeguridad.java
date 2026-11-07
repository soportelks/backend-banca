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

        helper.setFrom(remitente, "Banca Web");
        helper.setTo(destinoNormalizado);
        helper.setSubject("Código de Seguridad - " + tipoTransaccion.getDescripcion());

        String horaEnvio = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String contentId = "logoBancaImage";

        String badgePrueba = esEntornoPrueba
                ? "<div style='background-color: #fff3cd; color: #856404; padding: 8px 12px; margin-bottom: 15px; border-radius: 4px; font-size: 13px; font-weight: bold; border: 1px solid #ffeeba; text-align: center;'>"
                + "MODO PRUEBA: Notificación dirigida a correo de testing (" + destinoNormalizado + ")"
                + "</div>"
                : "";

        String htmlBody = "<div style='font-family: Arial, sans-serif; background-color: #f4f6f9; padding: 25px;'>"
                + "<div style='max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.08);'>"

                // Encabezado
                + "<div style='background-color: #2b3751; padding: 25px; text-align: center;'>"
                + "<img src='cid:" + contentId + "' alt='Logo' style='max-width: 160px; height: auto;'/>"
                + "<h2 style='color: #ffffff; margin: 15px 0 0 0; font-size: 20px; font-weight: 500;'>Autorización de Transacción</h2>"
                + "</div>"

                // Cuerpo del Mensaje
                + "<div style='padding: 30px; color: #333333;'>"
                + badgePrueba
                + "<p style='font-size: 15px; line-height: 1.5; margin-bottom: 20px;'>"
                + "Se ha solicitado autorización para ejecutar la siguiente operación: <strong>" + tipoTransaccion.getDescripcion() + "</strong>."
                + "</p>"
                + "<p style='font-size: 15px; margin-bottom: 10px; color: #555555;'>Tu código de seguridad temporal es:</p>"

                // Contenedor del Código OTP
                + "<div style='background: #f0f4ff; border: 2px dashed #3b82f6; border-radius: 8px; padding: 18px; text-align: center; margin: 20px 0;'>"
                + "<span style='font-size: 34px; font-weight: bold; letter-spacing: 6px; color: #1d4ed8; font-family: monospace;'>"
                + codigo
                + "</span>"
                + "</div>"

                + "<p style='font-size: 14px; color: #6b7280; text-align: center; margin-top: 15px;'>"
                + "Este código es de uso único y expirará en <strong>" + minutosExpiracion + " minutos</strong>."
                + "</p>"

                // Alerta de Seguridad
                + "<div style='background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px; margin-top: 25px; border-radius: 0 4px 4px 0;'>"
                + "<p style='font-size: 13px; color: #991b1b; margin: 0; line-height: 1.4;'>"
                + "<strong>Aviso de Seguridad:</strong> Si tú no solicitaste este código, es posible que alguien esté intentando acceder a tu cuenta. No compartas este código con ninguna persona; nuestro personal nunca te lo solicitará."
                + "</p>"
                + "</div>"
                + "</div>"

                // Pie de Página
                + "<div style='background-color: #f9fafb; padding: 15px; text-align: center; border-top: 1px solid #e5e7eb; font-size: 12px; color: #9ca3af;'>"
                + "<p style='margin: 0;'>Hora de emisión: " + horaEnvio + " (Hora Ciudad de México)</p>"
                + "<p style='margin: 5px 0 0 0;'>Banca Digital - Sistema de Verificación Seguro</p>"
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
            log.info("Código de seguridad enviado exitosamente al correo: {}", enmascarado);
        } catch (Exception e) {
            log.error("Fallo al enviar correo con código de seguridad a {}: {}", enmascarado, e.getMessage());
            throw new CodigoSeguridadException("No fue posible enviar el código de seguridad al correo configurado: " + e.getMessage(), e);
        }
    }
}

