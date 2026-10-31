package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

@Service
public class IMplEmailOtpService {

    private static final Logger LOGGER = Logger.getLogger(IMplEmailOtpService.class.getName());

    @Value("${email.logo.url}")
    private String logoUrl;

    @Value("${spring.mail.username}")
    private String sender;

    @Autowired
    private JavaMailSender mailSender;

    private final Map<String, OtpData> otpStorage = new HashMap<>();
    private static final int OTP_EXPIRATION_MINUTES = 2;

    public void sendOtp(String email) {
        int otp = generateOtp();
        otpStorage.put(email, new OtpData(otp, LocalDateTime.now()));
        sendEmailWithRetry(email, otp, 2);
    }

    private void sendEmailWithRetry(String email, int otp, int maxRetries) {
        int attempt = 0;
        Exception lastError = null;
        while (attempt <= maxRetries) {
            try {
                doSendEmail(email, otp);
                return; // éxito
            } catch (Exception e) {
                attempt++;
                lastError = e;
                LOGGER.warning("Intento " + attempt + " fallido al enviar OTP: " + e.getMessage());
                if (attempt <= maxRetries) {
                    try { Thread.sleep(1500L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        LOGGER.severe("No se pudo enviar el correo OTP después de " + maxRetries + " intentos.");
        if (lastError instanceof RuntimeException) throw (RuntimeException) lastError;
        throw new RuntimeException("Fallo al enviar correo OTP", lastError);
    }

    private void doSendEmail(String email, int otp) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(sender);
        helper.setTo(email);
        helper.setSubject("Código de Verificación");

        String horaActual = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Usa el mismo content ID que usarás en el HTML
        String contentId = "logoImage";

        String htmlMsg = "<div style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>"
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
                + "<div style='font-size: 36px; font-weight: bold; color: #007BFF; margin: 20px 0;'>" + otp
                + "</div>"
                + "<p style='font-size: 16px; color: #777;'>Este código expirará en <strong>2 minutos</strong>.</p>"
                + "</div>"

                // Pie azul
                + "<div style='background-color: #2b3751; padding: 15px; border-radius: 0 0 10px 10px; text-align: center;'>"
                + "<p style='color: white; font-size: 14px; margin: 0;'>Hora de envío: " + horaActual + "</p>"
                + "</div>"

                + "</div></div>";

        helper.setText(htmlMsg, true);

        ClassPathResource resource = new ClassPathResource("images/Logo_RDAM_blanco.png");
        helper.addInline(contentId, resource, "image/png");

        mailSender.send(message);
    }

    public boolean verifyOtp(String email, int enteredOtp) {
        if (!otpStorage.containsKey(email)) {
            return false;
        }

        OtpData otpData = otpStorage.get(email);
        LocalDateTime now = LocalDateTime.now();

        if (otpData.getTimestamp().plusMinutes(OTP_EXPIRATION_MINUTES).isBefore(now)) {
            otpStorage.remove(email);
            return false;
        }

        boolean isValid = otpData.getOtp() == enteredOtp;
        if (isValid) {
            otpStorage.remove(email);
        }
        return isValid;
    }

    private int generateOtp() {
        return new Random().nextInt(900000) + 100000;
    }

    private static class OtpData {
        private final int otp;
        private final LocalDateTime timestamp;

        public OtpData(int otp, LocalDateTime timestamp) {
            this.otp = otp;
            this.timestamp = timestamp;
        }

        public int getOtp() {
            return otp;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}
