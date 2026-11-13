package com.lksbaas.mx.service.impl;

import com.lksbaas.mx.exception.ErrorOperacionException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ImplFirmaServices {

    private static final String CAMPO_MONTO = "monto";
    private static final String CAMPO_TIPO_PAGO = "tipoPago";
    private static final List<String> CAMPOS_TEXTO_STP = List.of(
            "claveRastreo",
            "institucionContraparte",
            "empresa",
            "tipoCuentaOrdenante",
            "nombreOrdenante",
            "cuentaOrdenante",
            "rfcCurpOrdenante",
            "tipoCuentaBeneficiario",
            "nombreBeneficiario",
            "cuentaBeneficiario",
            "rfcCurpBeneficiario",
            "conceptoPago",
            "referenciaNumerica",
            "institucionOperante",
            CAMPO_TIPO_PAGO
    );

    @Value("${stp.emulacion.enabled:false}")
    private boolean stpEmulacionEnabled;

    public String firmarDatos(String data) {
        if (stpEmulacionEnabled) {
            // En modo emulación / pruebas locales no se requiere el archivo prod_rdam.pem
            return Base64.getEncoder().encodeToString(("MOCK_FIRMA_ELECTRONICA_STP_" + data.hashCode()).getBytes(StandardCharsets.UTF_8));
        }
        try {
            return getSign(data); // Usa tu lógica existente
        } catch (Exception e) {
            throw new ErrorOperacionException("Error al generar la firma: " + e.getMessage(), e);
        }
    }

    public String getSign(String cadenaOriginal) throws GeneralSecurityException, IOException {
        // Cargar la clave privada desde el archivo PEM

        RSAPrivateKey privateKey = loadPrivateKey("prod_rdam.pem");

        // Crear el objeto de firma con RSA y SHA-256
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(cadenaOriginal.getBytes(StandardCharsets.UTF_8));

        // Firmar los datos
        byte[] signatureBytes = sign.sign();

        // Convertir la firma a Base64
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    private RSAPrivateKey loadPrivateKey(String resourcePath) throws IOException, GeneralSecurityException {
        // Intenta obtener el archivo desde el classpath (src/main/resources)
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("No se encontró el archivo en resources: " + resourcePath);
        }

        byte[] keyBytes = inputStream.readAllBytes();
        inputStream.close();

        // Convertir a String usando UTF-8
        String privateKeyPEM = new String(keyBytes, StandardCharsets.UTF_8);

        // Limpiar la cadena para extraer solo la parte codificada en Base64
        privateKeyPEM = privateKeyPEM.replaceAll("(?m)^Bag Attributes.*\\R?", "")
                .replaceAll("(?m)^Key Attributes.*\\R?", "")
                .replaceAll("(?m)^\\s*friendlyName:.*\\R?", "")
                .replaceAll("(?m)^\\s*localKeyID:.*\\R?", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        // Decodificar la cadena en Base64
        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);

        // Generar la clave privada usando PKCS8EncodedKeySpec
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    /**
     * Normaliza el monto a 2 decimales y colapsa espacios múltiples en los campos de texto
     * para asegurar coincidencia exacta entre el JSON enviado a STP y la cadena firmada.
     */
    public void normalizarPagoStp(ObjectNode pagoNode) {
        if (pagoNode == null) {
            return;
        }
        if (pagoNode.hasNonNull(CAMPO_MONTO)) {
            pagoNode.put(CAMPO_MONTO, formatearMonto(pagoNode.get(CAMPO_MONTO).asText()));
        }
        for (String campo : CAMPOS_TEXTO_STP) {
            if (pagoNode.hasNonNull(campo)) {
                pagoNode.put(campo, normalizarTexto(pagoNode.get(campo).asText()));
            }
        }
    }

    /**
     * Formatea un monto numérico a exactamente dos decimales usando punto decimal.
     */
    public static String formatearMonto(String montoRaw) {
        if (montoRaw == null || montoRaw.isBlank()) {
            throw new ErrorOperacionException("El campo 'monto' es obligatorio para generar la firma STP");
        }
        try {
            BigDecimal monto = new BigDecimal(montoRaw.trim());
            return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            throw new ErrorOperacionException("El campo 'monto' no tiene un formato numérico válido: " + montoRaw, e);
        }
    }

    private static String normalizarTexto(String valor) {
        return valor == null ? "" : valor.replaceAll("\\s+", " ").trim();
    }

    private static String requerirCampoTexto(ObjectNode pagoNode, String campo) {
        if (pagoNode == null || !pagoNode.hasNonNull(campo)) {
            throw new ErrorOperacionException("Falta el campo obligatorio para firma STP: " + campo);
        }
        return normalizarTexto(pagoNode.get(campo).asText());
    }

    /**
     * Construye la cadena original requerida por STP aplicando formato monetario a 2 decimales
     * y normalización de espacios consistente con el payload enviado.
     */
    public String generarCadenaFirma(ObjectNode pagoNode) {
        String claveRastreo = requerirCampoTexto(pagoNode, "claveRastreo");
        String institucionContraparte = requerirCampoTexto(pagoNode, "institucionContraparte");
        String empresa = requerirCampoTexto(pagoNode, "empresa");
        String monto = formatearMonto(requerirCampoTexto(pagoNode, CAMPO_MONTO));
        String tipoPago = pagoNode.hasNonNull(CAMPO_TIPO_PAGO) && !pagoNode.get(CAMPO_TIPO_PAGO).asText().isBlank()
                ? normalizarTexto(pagoNode.get(CAMPO_TIPO_PAGO).asText())
                : "1";
        String tipoCuentaOrdenante = requerirCampoTexto(pagoNode, "tipoCuentaOrdenante");
        String nombreOrdenante = requerirCampoTexto(pagoNode, "nombreOrdenante");
        String cuentaOrdenante = requerirCampoTexto(pagoNode, "cuentaOrdenante");
        String rfcCurpOrdenante = requerirCampoTexto(pagoNode, "rfcCurpOrdenante");
        String tipoCuentaBeneficiario = requerirCampoTexto(pagoNode, "tipoCuentaBeneficiario");
        String nombreBeneficiario = requerirCampoTexto(pagoNode, "nombreBeneficiario");
        String cuentaBeneficiario = requerirCampoTexto(pagoNode, "cuentaBeneficiario");
        String rfcCurpBeneficiario = requerirCampoTexto(pagoNode, "rfcCurpBeneficiario");
        String conceptoPago = requerirCampoTexto(pagoNode, "conceptoPago");
        String referenciaNumerica = requerirCampoTexto(pagoNode, "referenciaNumerica");
        String institucionOperante = requerirCampoTexto(pagoNode, "institucionOperante");

        // Construir la cadena de firma
        return String.format("||%s|%s|||%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s||||||%s||||||%s||||||||",
                institucionContraparte,
                empresa,
                claveRastreo,
                institucionOperante,
                monto,
                tipoPago,
                tipoCuentaOrdenante,
                nombreOrdenante,
                cuentaOrdenante,
                rfcCurpOrdenante,
                tipoCuentaBeneficiario,
                nombreBeneficiario,
                cuentaBeneficiario,
                rfcCurpBeneficiario,
                conceptoPago,
                referenciaNumerica);
    }

}
