package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImplPagosMasivosServices {

    @Autowired
    private IDinamicoService service;

    @Autowired
    private ImplUtilsServices utils;

    @Autowired
    private ImplSaldoServices saldos;

    @Autowired
    private ImplFirmaServices firma;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private implComicionesServices comiciones;

    @Value("${url-pagos}")
    private String urlPagos;

    @Value("${url-saldos}")
    private String urlSaldos;

    @Value("${cveCaja}")
    private String cveCaja;

    @Value("${cveSucursal}")
    private String cveSucursal;

    @Value("${clienteDepocitosComision}")
    private String clientComicion;

    @Value("${usuarioMovimientos}")
    private String usuarioMovimientos;

    @Value("${usuarioComisiones}")
    private String usuarioComiciones;

    @Value("${query.spei.crud_orden_masivo}")
    private String queryInsertarDispersionesMasivo;

    public String insertarDispersionesMasivo(RequestParam request) {
        return service.execQueryDinamico1Param(request,
                queryInsertarDispersionesMasivo);
    }

    public String registrarSPEI(ObjectNode json) {
        try {
            String fechaOperacion = utils.obtenerFechaOperacion().get("fechaOperacion");

            ObjectNode clienteNode = (ObjectNode) json.get("cliente");
            boolean montoValido = saldos.validarSaldo(clienteNode);

            if (!montoValido) {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put("codigo", "1");
                respuesta.put("mensaje", "Saldo insuficiente para esta operacion");
                return mapper.writeValueAsString(respuesta);
            }

            ArrayNode pagosArray = (ArrayNode) json.get("pago");
            ObjectMapper mapper = new ObjectMapper();

            int exitosos = 0;
            int fallidos = 0;
            List<String> causasFallos = new ArrayList<>();

            List<Map<String, Object>> movimientosList = new ArrayList<>();
            List<Map<String, Object>> comisionesList = new ArrayList<>();
            List<Map<String, Object>> depocitosList = new ArrayList<>();

            for (JsonNode pago : pagosArray) {
                ObjectNode pagoNode = (ObjectNode) pago;

                String claveRastreoPago = (String) utils.generarUltimaClaveRastreo().get("nuevaClave");
                pagoNode.put("claveRastreo", claveRastreoPago);
                pagoNode.put("firma", firma.firmarDatos(firma.generarCadenaFirma(pagoNode)));

                String pagoEnviado = enviarPagos(pagoNode);
                JsonNode resultado = mapper.readTree(pagoEnviado);

                if (!resultado.has("resultado") || !resultado.get("resultado").has("id")
                        || resultado.get("resultado").get("id").asInt() <= 0) {
                    fallidos++;
                    causasFallos.add(
                            resultado.has("descripcionError")
                                    ? resultado.get("descripcionError").asText()
                                    : "Error desconocido al procesar el pago");
                    continue;
                }

                pagoNode.put("fechaOperacion", fechaOperacion);
                pagoNode.put("folioOrigen", utils.obtenerNumeroSecuencialDesdeClave(claveRastreoPago));

                ObjectNode root = mapper.createObjectNode();
                ObjectNode datosNode = mapper.createObjectNode();
                ArrayNode ordenesArray = mapper.createArrayNode();
                ordenesArray.add(pagoNode);

                datosNode.put("operacion", 1);
                datosNode.set("ordenes", ordenesArray);
                root.set("datos", datosNode);

                String finalPayload = mapper.writeValueAsString(root);
                JsonNode finalJsonNode = mapper.readTree(finalPayload);
                JsonNode datosNodeFromPayload = finalJsonNode.get("datos");

                RequestParam request = new RequestParam();
                request.setDatos(datosNodeFromPayload);

                String response = insertarDispersionesMasivo(request);
                JsonNode responseNode = mapper.readTree(response);

                if (!responseNode.isObject()) {
                    if (responseNode.isArray() && responseNode.size() > 0) {
                        responseNode = responseNode.get(0);
                    } else {
                        fallidos++;
                        causasFallos.add("Error desconocido al procesar el pago en base de datos");
                        continue;
                    }
                }

                if (responseNode.has("codigo") && responseNode.get("codigo").asInt() == 1) {
                    fallidos++;
                    causasFallos.add(responseNode.has("mensaje")
                            ? responseNode.get("mensaje").asText()
                            : "Error al registrar el pago");
                    continue;
                }

                // Movimiento
                int ordenID = responseNode.get("id").asInt();
                Double monto = responseNode.get("monto").asDouble();

                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("ordenid", ordenID);
                requestMap.put("cveCliente", clienteNode.get("datos").get("numero_cliente").asText());
                requestMap.put("usuario", usuarioMovimientos);
                requestMap.put("cveSucursal", cveSucursal);
                requestMap.put("cveCaja", cveCaja);

                List<Object> movimiento = Arrays.asList(
                        "03", "02", "0002", monto, null, 0,
                        null, null, null, null, fechaOperacion);

                requestMap.put("movimientos", List.of(movimiento));
                movimientosList.add(requestMap);

                Map<String, Object> comisionResponse = comiciones.obtenerComisionesPorNumeroCliente(
                        clienteNode.get("datos").get("numero_cliente").asText());

                if ((int) comisionResponse.get("codigo") != 0) {
                    throw new RuntimeException(comisionResponse.get("mensaje").toString());
                }

                List<Map<String, Object>> comisiones = (List<Map<String, Object>>) comisionResponse.get("info");
                Map<String, Object> primeraComision = comisiones.get(0);

                Object comisionValue = primeraComision.get("comision_spei");
                Boolean comisionStatus = Boolean.parseBoolean(primeraComision.get("estatus").toString());
                if (comisionValue != null && comisionStatus) {
                    BigDecimal comisionSpei = new BigDecimal(comisionValue.toString());
                    comisionSpei = comisionSpei.add(comisionSpei.multiply(new BigDecimal("0.16")))
                            .setScale(2, RoundingMode.HALF_UP);
                    if (comisionSpei.compareTo(BigDecimal.ZERO) > 0) {
                        Map<String, Object> requestMapComisiones = new HashMap<>();
                        requestMapComisiones.put("cveCliente", clienteNode.get("datos").get("numero_cliente").asText());
                        requestMapComisiones.put("usuario", usuarioComiciones);
                        requestMapComisiones.put("cveSucursal", cveSucursal);
                        requestMapComisiones.put("cveCaja", cveCaja);

                        List<Object> movimientoComision = Arrays.asList(
                                "03", "02", "0002", comisionSpei, null, 0,
                                null, null, null, null, fechaOperacion);

                        requestMapComisiones.put("movimientos", List.of(movimientoComision));
                        comisionesList.add(requestMapComisiones);

                        Map<String, Object> requestMapDepocito = new HashMap<>();
                        requestMapDepocito.put("cveCliente", clientComicion.toString());
                        requestMapDepocito.put("usuario", usuarioComiciones);
                        requestMapDepocito.put("cveSucursal", cveSucursal);
                        requestMapDepocito.put("cveCaja", cveCaja);

                        List<Object> movimientoDepocito = Arrays.asList(
                                "03", "01", "0002", comisionSpei, null, 0,
                                null, null, null, null, fechaOperacion);

                        requestMapDepocito.put("movimientos", List.of(movimientoDepocito));
                        depocitosList.add(requestMapDepocito);
                    }
                }
                exitosos++;
            }

            registrarMovimientosMasivos(movimientosList);

            comiciones.enviarMovimientosMasivosComisiones(comisionesList);

            comiciones.enviarMovimientosMasivosComisiones(depocitosList);

            ObjectNode resultadoFinal = mapper.createObjectNode();
            resultadoFinal.put("codigo", "0");
            resultadoFinal.put("mensaje", "Procesamiento completado");
            resultadoFinal.put("exitosos", exitosos);
            resultadoFinal.put("fallidos", fallidos);
            resultadoFinal.set("causasFallos", mapper.valueToTree(causasFallos));

            return mapper.writeValueAsString(resultadoFinal);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error al convertir el JSON a String", e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("El campo 'monto' no tiene un formato válido para double", e);
        }
    }

    public String enviarPagos(ObjectNode json) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String endpointUrl = this.urlPagos.trim();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ObjectNode> request = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.exchange(endpointUrl, HttpMethod.PUT, request, String.class);
            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"mensaje\": \"Error al validar la orden: " + e.getMessage() + "\", \"status\": 500}";
        }
    }

    public List<Map<String, Object>> registrarMovimientosMasivos(List<Map<String, Object>> requests) {
        List<Map<String, Object>> respuestas = new ArrayList<>();
        String sql = "SELECT tesoreria.spi_movimientocaja(?, ?, ?, ?, ?)";
        String sqlInsert = "SELECT spei.crud_ordenmovimiento(?, ?, ?)";

        for (Map<String, Object> request : requests) {
            try {
                Integer ordenID = (Integer) request.get("ordenid");
                String cveCliente = (String) request.get("cveCliente");
                String usuario = (String) request.get("usuario");
                String cveSucursal = (String) request.get("cveSucursal");
                String cveCaja = (String) request.get("cveCaja");
                List<List<Object>> movimientos = (List<List<Object>>) request.get("movimientos");

                if (cveCliente == null || usuario == null || cveSucursal == null || cveCaja == null
                        || movimientos == null || movimientos.isEmpty()) {
                    respuestas.add(Map.of("error", "Faltan datos obligatorios en la petición."));
                    continue;
                }

                String resultado = jdbcTemplate.execute((Connection connection) -> {
                    try {
                        String[][] movimientosArray = movimientos.stream()
                                .map(list -> list.stream().map(obj -> obj != null ? obj.toString() : null)
                                        .toArray(String[]::new))
                                .toArray(String[][]::new);
                        Array pgArray = connection.createArrayOf("text", movimientosArray);
                        return jdbcTemplate.queryForObject(sql, String.class, cveCliente, usuario, cveSucursal, cveCaja,
                                pgArray);
                    } catch (SQLException e) {
                        throw new RuntimeException("Error al crear el array de movimientos", e);
                    }
                });

                if (resultado == null) {
                    respuestas.add(Map.of("error", "La consulta no devolvió resultados."));
                    continue;
                }

                resultado = resultado.replaceAll("\\[\\d+:\\d+\\]=\\{|\\{|\\}", "");
                String[] valores = resultado.split(",");

                for (int i = 0; i < valores.length; i++) {
                    valores[i] = valores[i].trim();
                    if (i != 1) {
                        valores[i] = valores[i].replaceAll("^\"|\"$", "");
                    }
                }

                if (valores.length < 4) {
                    respuestas.add(Map.of("error", "Respuesta inesperada del procedimiento almacenado."));
                    continue;
                }

                int idPoliza = Integer.parseInt(valores[2]);
                int idMovimiento = Integer.parseInt(valores[3]);

                String insertResultado = jdbcTemplate.execute((Connection connection) -> {
                    try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
                        ps.setInt(1, ordenID);
                        ps.setInt(2, idPoliza);
                        ps.setInt(3, idMovimiento);
                        ps.execute();
                        return "Inserción exitosa";
                    } catch (SQLException e) {
                        throw new RuntimeException("Error al ejecutar la inserción", e);
                    }
                });

                respuestas.add(Map.of(
                        "message", "Movimiento registrado con éxito para el cliente: " + cveCliente,
                        "idPoliza", idPoliza,
                        "idMovimiento", idMovimiento,
                        "insertResultado", insertResultado));

            } catch (Exception e) {
                respuestas.add(Map.of("error", "Error interno: " + e.getMessage()));
            }
        }

        return respuestas;
    }
}
