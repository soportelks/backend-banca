package com.lksbaas.mx.service.impl;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ImplFirmaServices {

    public String firmarDatos(String data) {
        try {
            return getSign(data); // Usa tu lógica existente
        } catch (Exception e) {
            throw new RuntimeException("Error al generar la firma: " + e.getMessage(), e);
        }
    }

    public String getSign(String cadenaOriginal) throws Exception {
        // Cargar la clave privada desde el archivo PEM
        // RSAPrivateKey privateKey = loadPrivateKey("caja_rdam_privada.pem");

        RSAPrivateKey privateKey = loadPrivateKey("prod_rdam.pem");

        // Crear el objeto de firma con RSA y SHA-256
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(cadenaOriginal.getBytes());

        // Firmar los datos
        byte[] signatureBytes = sign.sign();

        // Convertir la firma a Base64
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    private RSAPrivateKey loadPrivateKey(String resourcePath) throws Exception {
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

    public String generarCadenaFirma(ObjectNode pagoNode) {

        String claveRastreo = pagoNode.get("claveRastreo").asText();
        String institucionContraparte = pagoNode.get("institucionContraparte").asText();
        String empresa = pagoNode.get("empresa").asText();
        String monto = pagoNode.get("monto").asText();
        String tipoCuentaOrdenante = pagoNode.get("tipoCuentaOrdenante").asText();
        String nombreOrdenante = pagoNode.get("nombreOrdenante").asText();
        String cuentaOrdenante = pagoNode.get("cuentaOrdenante").asText();
        String rfcCurpOrdenante = pagoNode.get("rfcCurpOrdenante").asText();
        String tipoCuentaBeneficiario = pagoNode.get("tipoCuentaBeneficiario").asText();
        String nombreBeneficiario = pagoNode.get("nombreBeneficiario").asText();
        String cuentaBeneficiario = pagoNode.get("cuentaBeneficiario").asText();
        String rfcCurpBeneficiario = pagoNode.get("rfcCurpBeneficiario").asText();
        String conceptoPago = pagoNode.get("conceptoPago").asText();
        String referenciaNumerica = pagoNode.get("referenciaNumerica").asText();
        String institucionOperante = pagoNode.get("institucionOperante").asText();

        // Construir la cadena de firma
        String cadenaFirma = String.format("||%s|%s|||%s|%s|%s|1|%s|%s|%s|%s|%s|%s|%s|%s||||||%s||||||%s||||||||",
                institucionContraparte.trim(),
                empresa.trim(),
                claveRastreo.trim(),
                institucionOperante.trim(),
                monto.trim(),
                tipoCuentaOrdenante.trim(),
                nombreOrdenante.trim(),
                cuentaOrdenante.trim(),
                rfcCurpOrdenante.trim(),
                tipoCuentaBeneficiario.trim(),
                nombreBeneficiario.trim(),
                cuentaBeneficiario.trim(),
                rfcCurpBeneficiario.trim(),
                conceptoPago.trim(),
                referenciaNumerica.trim());

        return cadenaFirma;
    }

}
