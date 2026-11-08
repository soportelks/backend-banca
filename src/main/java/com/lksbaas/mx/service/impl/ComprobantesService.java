package com.lksbaas.mx.service.impl;

import com.lksbaas.mx.exception.ErrorOperacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;

@Service
public class ComprobantesService {
    private static final String KEY_EMPRESA = "empresa";
    private static final String KEY_CLAVE_RASTREO = "claveRastreo";
    private static final String KEY_FECHA_OPERACION = "fechaOperacion";

    private static final Logger log = LoggerFactory.getLogger(ComprobantesService.class);


    @Value("${url-comprobante}")
    private String urlComprobante;

    @Value("${urlBanca}")
    private String urlBanca;

    private final ImplFirmaServices firma;

    public ComprobantesService(ImplFirmaServices firma) {
        this.firma = firma;
    }

    @Value("${stp.emulacion.enabled:false}")
    private boolean stpEmulacionEnabled;

    public String comprobantePagoSTP(ObjectNode datos) {
        try {
            // Validar y extraer valores desde el ObjectNode
            if (!datos.hasNonNull(KEY_EMPRESA) || !datos.hasNonNull(KEY_CLAVE_RASTREO)
                    || !datos.hasNonNull(KEY_FECHA_OPERACION)) {
                return "Faltan datos obligatorios";
            }

            String empresa = datos.get(KEY_EMPRESA).asText();
            String claveRastreo = datos.get(KEY_CLAVE_RASTREO).asText();
            int fechaOperacion = datos.get(KEY_FECHA_OPERACION).asInt();

            // Construir cadena a firmar
            String cadena = "||" + empresa + "|" + claveRastreo + "||" + fechaOperacion + "||";

            // Firmar la cadena
            String firmaDigital = firma.firmarDatos(cadena);

            // Armar JSON con firma incluida
            ObjectNode json = JsonNodeFactory.instance.objectNode();
            json.put(KEY_EMPRESA, empresa);
            json.put(KEY_CLAVE_RASTREO, claveRastreo);
            json.put(KEY_FECHA_OPERACION, fechaOperacion);
            json.put("firma", firmaDigital);

            // Enviar al servicio de comprobantes
            return obtenerComprobantePago(json);

        } catch (Exception e) {
            return "Error al procesar comprobante: " + e.getMessage();
        }
    }

    // Método para obtener el comprobante de pago

    private String resolverTipoAmbiente() {
        if (stpEmulacionEnabled) {
            return "SIMULADA";
        }
        boolean esProd = this.urlComprobante != null && this.urlComprobante.contains("prod");
        return esProd ? "PRODUCCIÓN" : "PRODUCCIÓN / DEMO";
    }

    public String obtenerComprobantePago(ObjectNode json) {
        String tipo = resolverTipoAmbiente();

        if (stpEmulacionEnabled) {
            String mockResp = "{\"mensaje\":\"Comprobante emulado en modo prueba STP\",\"estado\":\"EXITO\",\"folio\":\"MOCK-STP\"}";
            log.info(">>> [STP COMPROBANTE - {}] LO QUE SE MANDÓ:\n{}", tipo, json);
            log.info("<<< [STP COMPROBANTE - {}] LO QUE REGRESA STP:\n{}", tipo, mockResp);
            return mockResp;
        }
        if (urlComprobante == null || urlComprobante.trim().isEmpty()) {
            throw new ErrorOperacionException("URL de comprobante STP no configurada");
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            String endpointUrl = urlComprobante.trim();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ObjectNode> request = new HttpEntity<>(json, headers);

            log.info(">>> [STP COMPROBANTE - {}] LO QUE SE MANDÓ:\n{}", tipo, json);

            // Realiza la solicitud POST y obtiene la respuesta
            ResponseEntity<String> response = restTemplate.exchange(endpointUrl, HttpMethod.POST, request,
                    String.class);
            String responseBody = response.getBody();

            log.info("<<< [STP COMPROBANTE - {}] LO QUE REGRESA STP:\n{}", tipo, responseBody);

            return responseBody; // Retorna el cuerpo de la respuesta como String

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("<<< [STP COMPROBANTE - {} - ERROR HTTP {}] LO QUE REGRESA STP:\n{}", tipo, e.getRawStatusCode(), errorBody);
            return !errorBody.isBlank() ? errorBody : "Error al obtener comprobante STP: " + e.getMessage();
        } catch (Exception e) {
            log.error("<<< [STP COMPROBANTE - {} - ERROR] LO QUE REGRESA STP:\n{}", tipo, e.getMessage());
            return String.format("Error al obtener el comprobante: %s", e.getMessage());
        }
    }

    // Método para enviar el comprobante por correo electrónico
    public Map<String, String> enviarComprobantePorEmail(ObjectNode json) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String endpointUrl = urlBanca + "/api/auth/retiro";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ObjectNode> request = new HttpEntity<>(json, headers);

            // Realiza la solicitud POST y obtiene la respuesta
            ResponseEntity<String> response = restTemplate.exchange(endpointUrl, HttpMethod.POST, request,
                    String.class);

            // Crea la respuesta con un mensaje de éxito
            Map<String, String> respuesta = new HashMap<>();
            respuesta.put("mensaje", "Enviado! " + response.getBody());

            return respuesta;

        } catch (Exception e) {
            // Si ocurre un error, se retorna un mensaje de error
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("mensaje", "Error al obtener el comprobante: " + e.getMessage());
            return errorResponse;
        }
    }
}
