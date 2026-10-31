package com.lksbaas.mx.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImplPagosMasivosServices {

    private static final Logger log = LoggerFactory.getLogger(ImplPagosMasivosServices.class);

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

    @Value("${stp.emulacion.enabled:false}")
    private boolean stpEmulacionEnabled;

    public String insertarDispersionesMasivo(RequestParam request) {
        return service.execQueryDinamico1Param(request, queryInsertarDispersionesMasivo);
    }

    public String registrarSPEI(ObjectNode json) {
        ObjectMapper mapper = new ObjectMapper();
        String idempotencyKey = null;

        try {
            String fechaOperacion = utils.obtenerFechaOperacion().get("fechaOperacion");

            ObjectNode clienteNode = (ObjectNode) json.get("cliente");
            String numeroCliente = clienteNode.get("datos").get("numero_cliente").asText();

            ArrayNode pagosArray = (ArrayNode) json.get("pago");
            if (pagosArray == null || pagosArray.isEmpty()) {
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put("codigo", "1");
                respuesta.put("mensaje", "La lista de pagos se encuentra vacía");
                return mapper.writeValueAsString(respuesta);
            }

            // 1. Obtener o calcular clave de idempotencia
            if (json.hasNonNull("idempotencyKey") && !json.get("idempotencyKey").asText().isBlank()) {
                idempotencyKey = json.get("idempotencyKey").asText();
            } else {
                idempotencyKey = "LOTE_" + numeroCliente + "_" + fechaOperacion + "_" + pagosArray.size() + "_" + Math.abs(json.toString().hashCode());
            }

            // 2. Control de Idempotencia: Verificar si el lote ya fue procesado o se encuentra en curso
            try {
                List<Map<String, Object>> lotes = jdbcTemplate.queryForList(
                        "SELECT id, estatus, respuesta_json FROM spei.lote_dispersion WHERE idempotency_key = ?",
                        idempotencyKey);

                if (!lotes.isEmpty()) {
                    Map<String, Object> lote = lotes.get(0);
                    String estatus = (String) lote.get("estatus");

                    if ("PROCESANDO".equalsIgnoreCase(estatus)) {
                        log.warn("Intento de doble dispersión bloqueado para el lote idempotente: {}", idempotencyKey);
                        ObjectNode respuesta = mapper.createObjectNode();
                        respuesta.put("codigo", "1");
                        respuesta.put("mensaje", "Este lote de nómina ya está en proceso de dispersión. Por favor espera unos momentos.");
                        return mapper.writeValueAsString(respuesta);
                    } else if ("COMPLETADO".equalsIgnoreCase(estatus)) {
                        log.info("Lote {} ya completado previamente. Retornando respuesta guardada.", idempotencyKey);
                        String respuestaPrevia = (String) lote.get("respuesta_json");
                        if (respuestaPrevia != null && !respuestaPrevia.isBlank()) {
                            return respuestaPrevia;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Aviso al verificar lote_dispersion: {}", e.getMessage());
            }

            // 3. Validar saldo disponible del cliente
            boolean montoValido = saldos.validarSaldo(clienteNode);
            if (!montoValido) {
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put("codigo", "1");
                respuesta.put("mensaje", "Saldo insuficiente para esta operacion");
                return mapper.writeValueAsString(respuesta);
            }

            // 4. Calcular suma total exacta con BigDecimal
            BigDecimal sumaTotalMontos = BigDecimal.ZERO;
            for (JsonNode pago : pagosArray) {
                if (pago.hasNonNull("monto")) {
                    sumaTotalMontos = sumaTotalMontos.add(new BigDecimal(pago.get("monto").asText().trim()));
                }
            }

            // 5. Registrar lote en estado PROCESANDO en la base de datos
            try {
                jdbcTemplate.update("""
                    INSERT INTO spei.lote_dispersion (idempotency_key, cve_cliente, total_ordenes, monto_total, estatus, fecha_creacion, fecha_actualizacion)
                    VALUES (?, ?, ?, ?, 'PROCESANDO', NOW(), NOW())
                    ON CONFLICT (idempotency_key) DO UPDATE SET estatus = 'PROCESANDO', fecha_actualizacion = NOW()
                """, idempotencyKey, numeroCliente, pagosArray.size(), sumaTotalMontos);
            } catch (Exception e) {
                log.warn("No se pudo registrar estado PROCESANDO en lote_dispersion: {}", e.getMessage());
            }

            // 6. Consultar comisiones del cliente UNA sola vez antes del ciclo
            BigDecimal comisionSpei = BigDecimal.ZERO;
            boolean aplicaComision = false;
            try {
                Map<String, Object> comisionResponse = comiciones.obtenerComisionesPorNumeroCliente(numeroCliente);
                if (comisionResponse != null && (int) comisionResponse.get("codigo") == 0) {
                    List<Map<String, Object>> comisionesListBD = (List<Map<String, Object>>) comisionResponse.get("info");
                    if (comisionesListBD != null && !comisionesListBD.isEmpty()) {
                        Map<String, Object> primeraComision = comisionesListBD.get(0);
                        Object comisionValue = primeraComision.get("comision_spei");
                        Boolean comisionStatus = primeraComision.get("estatus") != null && Boolean.parseBoolean(primeraComision.get("estatus").toString());

                        if (comisionValue != null && comisionStatus) {
                            comisionSpei = new BigDecimal(comisionValue.toString());
                            comisionSpei = comisionSpei.add(comisionSpei.multiply(new BigDecimal("0.16")))
                                    .setScale(2, RoundingMode.HALF_UP);
                            aplicaComision = comisionSpei.compareTo(BigDecimal.ZERO) > 0;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Cliente {} sin comisiones activas o error al consultar comisiones: {}", numeroCliente, e.getMessage());
            }

            int exitosos = 0;
            int fallidos = 0;
            List<String> causasFallos = new ArrayList<>();

            List<Map<String, Object>> movimientosList = new ArrayList<>();
            List<Map<String, Object>> comisionesList = new ArrayList<>();
            List<Map<String, Object>> depocitosList = new ArrayList<>();

            // 7. Ciclo de pagos hacia STP
            for (JsonNode pago : pagosArray) {
                ObjectNode pagoNode = (ObjectNode) pago;

                String claveRastreoPago = (String) utils.generarUltimaClaveRastreo().get("nuevaClave");
                pagoNode.put("claveRastreo", claveRastreoPago);
                pagoNode.put("firma", firma.firmarDatos(firma.generarCadenaFirma(pagoNode)));

                String pagoEnviado = enviarPagos(pagoNode);
                JsonNode resultado = mapper.readTree(pagoEnviado);

                boolean pagoValido = false;
                if (resultado.has("resultado") && resultado.get("resultado").has("id")
                        && resultado.get("resultado").get("id").asLong() > 0) {
                    pagoValido = true;
                } else if (resultado.has("id") && resultado.get("id").asLong() > 0) {
                    pagoValido = true;
                }

                if (!pagoValido) {
                    fallidos++;
                    String causa = resultado.has("descripcionError")
                            ? resultado.get("descripcionError").asText()
                            : (resultado.has("mensaje") ? resultado.get("mensaje").asText() : "Error desconocido al procesar el pago");
                    causasFallos.add(causa);
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

                int ordenID = responseNode.get("id").asInt();
                Double monto = responseNode.get("monto").asDouble();

                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("ordenid", ordenID);
                requestMap.put("cveCliente", numeroCliente);
                requestMap.put("usuario", usuarioMovimientos);
                requestMap.put("cveSucursal", cveSucursal);
                requestMap.put("cveCaja", cveCaja);

                List<Object> movimiento = Arrays.asList(
                        "03", "02", "0002", monto, null, 0,
                        null, null, null, null, fechaOperacion);

                requestMap.put("movimientos", List.of(movimiento));
                movimientosList.add(requestMap);

                if (aplicaComision) {
                    Map<String, Object> requestMapComisiones = new HashMap<>();
                    requestMapComisiones.put("cveCliente", numeroCliente);
                    requestMapComisiones.put("usuario", usuarioComiciones);
                    requestMapComisiones.put("cveSucursal", cveSucursal);
                    requestMapComisiones.put("cveCaja", cveCaja);

                    List<Object> movimientoComision = Arrays.asList(
                            "03", "02", "0002", comisionSpei, null, 0,
                            null, null, null, null, fechaOperacion);

                    requestMapComisiones.put("movimientos", List.of(movimientoComision));
                    comisionesList.add(requestMapComisiones);

                    Map<String, Object> requestMapDepocito = new HashMap<>();
                    requestMapDepocito.put("cveCliente", clientComicion);
                    requestMapDepocito.put("usuario", usuarioComiciones);
                    requestMapDepocito.put("cveSucursal", cveSucursal);
                    requestMapDepocito.put("cveCaja", cveCaja);

                    List<Object> movimientoDepocito = Arrays.asList(
                            "03", "01", "0002", comisionSpei, null, 0,
                            null, null, null, null, fechaOperacion);

                    requestMapDepocito.put("movimientos", List.of(movimientoDepocito));
                    depocitosList.add(requestMapDepocito);
                }

                exitosos++;
            }

            // 8. Aplicar movimientos contables en caja
            if (!movimientosList.isEmpty()) {
                registrarMovimientosMasivos(movimientosList);
            }
            if (!comisionesList.isEmpty()) {
                comiciones.enviarMovimientosMasivosComisiones(comisionesList);
            }
            if (!depocitosList.isEmpty()) {
                comiciones.enviarMovimientosMasivosComisiones(depocitosList);
            }

            ObjectNode resultadoFinal = mapper.createObjectNode();
            resultadoFinal.put("codigo", "0");
            resultadoFinal.put("mensaje", "Procesamiento completado");
            resultadoFinal.put("exitosos", exitosos);
            resultadoFinal.put("fallidos", fallidos);
            resultadoFinal.set("causasFallos", mapper.valueToTree(causasFallos));

            String respuestaStr = mapper.writeValueAsString(resultadoFinal);

            // 9. Actualizar lote como COMPLETADO
            try {
                jdbcTemplate.update(
                        "UPDATE spei.lote_dispersion SET estatus = 'COMPLETADO', respuesta_json = ?, fecha_actualizacion = NOW() WHERE idempotency_key = ?",
                        respuestaStr, idempotencyKey);
            } catch (Exception e) {
                log.warn("No se pudo actualizar estado COMPLETADO en lote_dispersion: {}", e.getMessage());
            }

            return respuestaStr;

        } catch (JsonProcessingException e) {
            marcarLoteFallido(idempotencyKey);
            throw new RuntimeException("Error al procesar el JSON de la nómina masiva", e);
        } catch (NumberFormatException e) {
            marcarLoteFallido(idempotencyKey);
            throw new RuntimeException("Formato numérico inválido en montos de nómina", e);
        } catch (Exception e) {
            marcarLoteFallido(idempotencyKey);
            log.error("Error durante la dispersión masiva: {}", e.getMessage(), e);
            throw new RuntimeException("Error al ejecutar la dispersión masiva: " + e.getMessage(), e);
        }
    }

    private void marcarLoteFallido(String idempotencyKey) {
        if (idempotencyKey != null) {
            try {
                jdbcTemplate.update(
                        "UPDATE spei.lote_dispersion SET estatus = 'FALLIDO', fecha_actualizacion = NOW() WHERE idempotency_key = ?",
                        idempotencyKey);
            } catch (Exception ex) {
                log.warn("No se pudo marcar lote como FALLIDO: {}", ex.getMessage());
            }
        }
    }

    public String enviarPagos(ObjectNode json) {
        if (stpEmulacionEnabled) {
            long mockStpId = (System.currentTimeMillis() % 100000000) + 1000;
            return String.format("{\"resultado\":{\"id\":%d,\"descripcionError\":null},\"id\":%d,\"mensaje\":\"Operacion emulada STP (MOCK)\"}", mockStpId, mockStpId);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String endpointUrl = this.urlPagos.trim();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ObjectNode> request = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.exchange(endpointUrl, HttpMethod.PUT, request, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error al enviar orden a STP: {}", e.getMessage());
            return "{\"mensaje\": \"Error al validar la orden en STP: " + e.getMessage() + "\", \"status\": 500}";
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

                // Uso directo de la Connection para no pedir una segunda conexión al pool de HikariCP
                String resultado = jdbcTemplate.execute((Connection connection) -> {
                    String[][] movimientosArray = movimientos.stream()
                            .map(list -> list.stream().map(obj -> obj != null ? obj.toString() : null)
                                    .toArray(String[]::new))
                            .toArray(String[][]::new);
                    Array pgArray = connection.createArrayOf("text", movimientosArray);
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        ps.setString(1, cveCliente);
                        ps.setString(2, usuario);
                        ps.setString(3, cveSucursal);
                        ps.setString(4, cveCaja);
                        ps.setArray(5, pgArray);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? rs.getString(1) : null;
                        }
                    } finally {
                        pgArray.free();
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
                    }
                });

                respuestas.add(Map.of(
                        "message", "Movimiento registrado con éxito para el cliente: " + cveCliente,
                        "idPoliza", idPoliza,
                        "idMovimiento", idMovimiento,
                        "insertResultado", insertResultado));

            } catch (Exception e) {
                log.error("Error al registrar movimiento masivo: {}", e.getMessage());
                respuestas.add(Map.of("error", "Error interno: " + e.getMessage()));
            }
        }

        return respuestas;
    }
}
