package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Value("${url-comprobante}")
    private String urlComprobante;

    @Value("${urlBanca}")
    private String urlBanca;

    @Autowired
    private ImplFirmaServices firma;

    public String comprobantePagoSTP(ObjectNode datos) {
        try {
            // Validar y extraer valores desde el ObjectNode
            if (!datos.hasNonNull("empresa") || !datos.hasNonNull("claveRastreo")
                    || !datos.hasNonNull("fechaOperacion")) {
                return "Faltan datos obligatorios";
            }

            String empresa = datos.get("empresa").asText();
            String claveRastreo = datos.get("claveRastreo").asText();
            int fechaOperacion = datos.get("fechaOperacion").asInt();

            // Construir cadena a firmar
            String cadena = "||" + empresa + "|" + claveRastreo + "||" + fechaOperacion + "||";

            // Firmar la cadena
            String firmaDigital = firma.firmarDatos(cadena);

            // Armar JSON con firma incluida
            ObjectNode json = JsonNodeFactory.instance.objectNode();
            json.put("empresa", empresa);
            json.put("claveRastreo", claveRastreo);
            json.put("fechaOperacion", fechaOperacion);
            json.put("firma", firmaDigital);

            // Enviar al servicio de comprobantes
            return obtenerComprobantePago(json);

        } catch (Exception e) {
            return "Error al procesar comprobante: " + e.getMessage();
        }
    }

    // Método para obtener el comprobante de pago
    public String obtenerComprobantePago(ObjectNode json) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String endpointUrl = urlComprobante.trim();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ObjectNode> request = new HttpEntity<>(json, headers);

            // Realiza la solicitud POST y obtiene la respuesta
            ResponseEntity<String> response = restTemplate.exchange(endpointUrl, HttpMethod.POST, request,
                    String.class);

            return response.getBody(); // Retorna el cuerpo de la respuesta como String

        } catch (Exception e) {
            // Si ocurre un error, se retorna un mensaje de error
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
