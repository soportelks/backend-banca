package com.lksbaas.mx.service.impl;

import com.lksbaas.mx.exception.ErrorOperacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImplPagosServices {
    private static final String KEY_DATOS = "datos";
    private static final String KEY_CODIGO = "codigo";
    private static final String KEY_CVE_CAJA = "cveCaja";
    private static final String KEY_MENSAJE = "mensaje";
    private static final String KEY_USUARIO = "usuario";
    private static final String KEY_RESULTADO = "resultado";
    private static final String KEY_CVE_CLIENTE = "cveCliente";
    private static final String KEY_CVE_SUCURSAL = "cveSucursal";
    private static final String KEY_MOVIMIENTOS = "movimientos";
    private static final String KEY_NUMERO_CLIENTE = "numero_cliente";
    private static final String KEY_DESCRIPCION_ERROR = "descripcionError";


    private static final Logger log = LoggerFactory.getLogger(ImplPagosServices.class);

    private final IDinamicoService service;

    private final ImplUtilsServices utils;

    private final ImplSaldoServices saldos;

    private final ImplFirmaServices firma;

    private final JdbcTemplate jdbcTemplate;

    private final ImplComicionesServices comiciones;

    public ImplPagosServices(IDinamicoService service, ImplUtilsServices utils, ImplSaldoServices saldos, ImplFirmaServices firma, JdbcTemplate jdbcTemplate, ImplComicionesServices comiciones) {
        this.service = service;
        this.utils = utils;
        this.saldos = saldos;
        this.firma = firma;
        this.jdbcTemplate = jdbcTemplate;
        this.comiciones = comiciones;
    }

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

    @Value("${stp.emulacion.enabled:false}")
    private boolean stpEmulacionEnabled;

    @Value("${query.spei.crud_orden_masivo}")
    private String queryInsertarDispersionesMasivo;

    public String insertarDispersionesMasivo(RequestParam request) {
        return service.execQueryDinamico1Param(request,
                queryInsertarDispersionesMasivo);
    }

    public String registrarSPEI(ObjectNode json) {
        try {
            String fechaOperacion = utils.obtenerFechaOperacion().get("fechaOperacion");
            String claveRastreo = (String) utils.generarUltimaClaveRastreo().get("nuevaClave");

            ObjectNode pagoNode = (ObjectNode) json.get("pago");
            ObjectNode clienteNode = (ObjectNode) json.get("cliente");
            boolean montoValido = saldos.validarSaldo(clienteNode);

            if (!montoValido) {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put(KEY_CODIGO, "1");
                respuesta.put(KEY_MENSAJE, "Saldo insuficiente para esta operacion");
                return mapper.writeValueAsString(respuesta);
            }

            pagoNode.put("claveRastreo", claveRastreo);
            firma.normalizarPagoStp(pagoNode);
            pagoNode.put("firma", firma.firmarDatos(firma.generarCadenaFirma(pagoNode)));
            String pagoEnviado = enviarPagos(pagoNode);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode resultado = mapper.readTree(pagoEnviado);

            // Regla STP: Solo se considera exitoso si STP retorna un ID positivo (> 0).
            // Si el ID es negativo (<= 0, ej. -53), nulo o viene con error, es rechazo de STP
            // y bajo ninguna circunstancia debe registrarse ni descontar saldo.
            long stpId = -1;
            String errorDesc = null;

            if (resultado != null) {
                // 1. Revisar si viene dentro del nodo "resultado" (id negativo, ej. -53, y descripcionError)
                if (resultado.has(KEY_RESULTADO) && resultado.get(KEY_RESULTADO).isObject()) {
                    JsonNode resObj = resultado.get(KEY_RESULTADO);
                    if (resObj.has("id") && !resObj.get("id").isNull()) {
                        stpId = resObj.get("id").asLong();
                    }
                    if (resObj.hasNonNull(KEY_DESCRIPCION_ERROR)) {
                        errorDesc = resObj.get(KEY_DESCRIPCION_ERROR).asText();
                    }
                }
                // 2. Revisar si viene a nivel raíz (id negativo y descripcionError)
                if (stpId <= 0 && resultado.has("id") && !resultado.get("id").isNull()) {
                    stpId = resultado.get("id").asLong();
                }
                if ((errorDesc == null || errorDesc.isBlank()) && resultado.hasNonNull(KEY_DESCRIPCION_ERROR)) {
                    errorDesc = resultado.get(KEY_DESCRIPCION_ERROR).asText();
                }
                if ((errorDesc == null || errorDesc.isBlank()) && resultado.hasNonNull(KEY_MENSAJE)) {
                    errorDesc = resultado.get(KEY_MENSAJE).asText();
                }
                if ((errorDesc == null || errorDesc.isBlank()) && resultado.hasNonNull("error")) {
                    errorDesc = resultado.get("error").asText();
                }
            }

            if (stpId <= 0) {
                String mensajeFinal;
                if (errorDesc != null && !errorDesc.isBlank()) {
                    mensajeFinal = errorDesc;
                } else {
                    mensajeFinal = "STP rechazó la operación" + (stpId != -1 ? " con código de error " + stpId : "");
                }

                ObjectNode respuestaError = mapper.createObjectNode();
                respuestaError.put(KEY_CODIGO, "1");
                respuestaError.put(KEY_MENSAJE, mensajeFinal);

                ObjectNode infoNode = mapper.createObjectNode();
                infoNode.put(KEY_MENSAJE, mensajeFinal);
                infoNode.put("id", stpId);
                respuestaError.set("info", infoNode);

                log.warn("[STP RECHAZO ORDEN INDIVIDUAL] stpId: {} | mensaje: {}", stpId, mensajeFinal);
                log.error(">>> [STP ERROR DETECTADO] stpId: {} | Mensaje: {}", stpId, mensajeFinal);
                return mapper.writeValueAsString(respuestaError);
            }

            pagoNode.put("fechaOperacion", fechaOperacion);
            pagoNode.put("folioOrigen", utils.obtenerNumeroSecuencialDesdeClave(claveRastreo));

            ObjectNode root = mapper.createObjectNode();
            ObjectNode datosNode = mapper.createObjectNode();
            ArrayNode ordenesArray = mapper.createArrayNode();

            ordenesArray.add(pagoNode);
            datosNode.put("operacion", 1);
            datosNode.set("ordenes", ordenesArray);
            root.set(KEY_DATOS, datosNode);

            String finalPayload = mapper.writeValueAsString(root);

            JsonNode finalJsonNode = mapper.readTree(finalPayload);
            JsonNode datosNodeFromPayload = finalJsonNode.get(KEY_DATOS);

            RequestParam request = new RequestParam();
            request.setDatos(datosNodeFromPayload);

            String response = insertarDispersionesMasivo(request);
            JsonNode responseNode = mapper.readTree(response);

            // Verificar si la respuesta es un objeto JSON
            if (!responseNode.isObject()) {
                // Si no es un objeto, verificar si es un array
                if (responseNode.isArray() && responseNode.size() > 0) {
                    responseNode = responseNode.get(0); // Procesar el primer elemento del array
                } else {
                    // Si ni siquiera es un array, es un error
                    ObjectNode respuestaError = mapper.createObjectNode();
                    respuestaError.put(KEY_CODIGO, "1");
                    respuestaError.put(KEY_MENSAJE, "Error desconocido al procesar el pago en base de datos");
                    return mapper.writeValueAsString(respuestaError);
                }
            }

            // Si la respuesta tiene código de error
            if (responseNode.has(KEY_CODIGO)) {
                int codigo = responseNode.get(KEY_CODIGO).asInt();
                if (codigo == 1) {
                    ObjectNode respuestaError = mapper.createObjectNode();
                    respuestaError.put(KEY_CODIGO, "1");
                    respuestaError.put(KEY_MENSAJE, responseNode.has(KEY_MENSAJE) ? responseNode.get(KEY_MENSAJE).asText()
                            : "Error al registrar el pago");
                    return mapper.writeValueAsString(respuestaError);
                }
            }

            // Obtener los datos necesarios de la respuesta
            int id = responseNode.get("id").asInt();
            Double monto = responseNode.get("monto").asDouble();

            // Construir el mapa de solicitud para enviar a enviarMovimientos
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("ordenid", id);
            requestMap.put(KEY_CVE_CLIENTE, clienteNode.get(KEY_DATOS).get(KEY_NUMERO_CLIENTE).asText());
            requestMap.put(KEY_USUARIO, usuarioMovimientos);
            requestMap.put(KEY_CVE_SUCURSAL, cveSucursal);
            requestMap.put(KEY_CVE_CAJA, cveCaja);

            // Construir el array de movimientos
            List<List<Object>> movimientos = new ArrayList<>();
            List<Object> movimiento = new ArrayList<>();
            movimiento.add("03"); // Código de movimiento
            movimiento.add("02"); // Código de pago
            movimiento.add("0002"); // Código de cuenta
            movimiento.add(monto); // Monto
            movimiento.add(null); // Campo vacío
            movimiento.add(0); // Campo de tipo
            movimiento.add(null); // Campo vacío
            movimiento.add(null); // Campo vacío
            movimiento.add(null); // Campo vacío
            movimiento.add(null); // Campo vacío
            movimiento.add(fechaOperacion); // Fecha de operación

            movimientos.add(movimiento);
            requestMap.put(KEY_MOVIMIENTOS, movimientos);

            // Llamar al método enviarMovimientos y obtener el resultado
            Map<String, Object> responseMovimientos = enviarMovimientos(requestMap);

            // Validar el resultado de enviarMovimientos
            ObjectMapper responseMapper = new ObjectMapper();
            ObjectNode respuestaFinal = responseMapper.createObjectNode();

            if (responseMovimientos.containsKey(KEY_CODIGO)
                    && Integer.parseInt(responseMovimientos.get(KEY_CODIGO).toString()) == 0) {
                // Éxito
                respuestaFinal.put(KEY_CODIGO, "0");
                respuestaFinal.put(KEY_MENSAJE, responseMovimientos.get(KEY_MENSAJE).toString());
                respuestaFinal.set("info", responseMapper.valueToTree(responseMovimientos.get("info")));
            } else {
                // Error
                respuestaFinal.put(KEY_CODIGO, "1");
                respuestaFinal.put(KEY_MENSAJE,
                        responseMovimientos.get(KEY_MENSAJE) != null ? responseMovimientos.get(KEY_MENSAJE).toString()
                                : "Ocurrió un error al registrar los movimientos");
            }

            Map<String, Object> comisionResponse = comiciones.obtenerComisionesPorNumeroCliente(
                    clienteNode.get(KEY_DATOS).get(KEY_NUMERO_CLIENTE).asText());

            // Validar si la consulta fue exitosa
            if ((int) comisionResponse.get(KEY_CODIGO) != 0) {
                // Manejar el error (puedes lanzar excepción o retornar mensaje)
                throw new ErrorOperacionException(comisionResponse.get(KEY_MENSAJE).toString());
            }

            // Obtener la lista de comisiones desde "info"
            List<Map<String, Object>> comisiones = (List<Map<String, Object>>) comisionResponse.get("info");

            // Aquí puedes usar la comisión que necesites, por ejemplo la primera:
            Map<String, Object> primeraComision = comisiones.get(0);
            BigDecimal comisionSpei = new BigDecimal(primeraComision.get("comision_spei").toString());
            boolean comisionStatus = Boolean.parseBoolean(primeraComision.get("estatus").toString());
            comisionSpei = comisionSpei.add(comisionSpei.multiply(new BigDecimal("0.16")))
                    .setScale(2, RoundingMode.HALF_UP);

            // Validar si la comisión es null o igual a cero
            if (comisionSpei.compareTo(BigDecimal.ZERO) > 0 && comisionStatus) {
                // Construir el mapa de solicitud para enviar a enviarMovimientos
                Map<String, Object> requestMapComiciones = new HashMap<>();
                requestMapComiciones.put(KEY_CVE_CLIENTE, clienteNode.get(KEY_DATOS).get(KEY_NUMERO_CLIENTE).asText());
                requestMapComiciones.put(KEY_USUARIO, usuarioComiciones);
                requestMapComiciones.put(KEY_CVE_SUCURSAL, cveSucursal);
                requestMapComiciones.put(KEY_CVE_CAJA, cveCaja);

                // Construir el array de movimientos
                List<List<Object>> movimientosComicion = new ArrayList<>();
                List<Object> movimientoComicion = new ArrayList<>();
                movimientoComicion.add("03"); // Código de movimiento
                movimientoComicion.add("02"); // Código de pago
                movimientoComicion.add("0002"); // Código de cuenta
                movimientoComicion.add(comisionSpei); // Monto
                movimientoComicion.add(null); // Campo vacío
                movimientoComicion.add(0); // Campo de tipo
                movimientoComicion.add(null); // Campo vacío
                movimientoComicion.add(null); // Campo vacío
                movimientoComicion.add(null); // Campo vacío
                movimientoComicion.add(null); // Campo vacío
                movimientoComicion.add(fechaOperacion); // Fecha de operación

                movimientosComicion.add(movimientoComicion);
                requestMapComiciones.put(KEY_MOVIMIENTOS, movimientosComicion);

                comiciones.procesarMovimientoComision(requestMapComiciones);

                Map<String, Object> requestMapSegundaComision = new HashMap<>();
                requestMapSegundaComision.put(KEY_CVE_CLIENTE, clientComicion);
                requestMapSegundaComision.put(KEY_USUARIO, usuarioComiciones);
                requestMapSegundaComision.put(KEY_CVE_SUCURSAL, cveSucursal);
                requestMapSegundaComision.put(KEY_CVE_CAJA, cveCaja);

                List<List<Object>> movimientosSegundaComision = new ArrayList<>();
                List<Object> movimientoSegundaComision = new ArrayList<>();
                movimientoSegundaComision.add("03"); // Código de movimiento
                movimientoSegundaComision.add("01"); // Código de pago
                movimientoSegundaComision.add("0002"); // Código de cuenta diferente (por ejemplo)
                movimientoSegundaComision.add(comisionSpei); // Monto
                movimientoSegundaComision.add(null);
                movimientoSegundaComision.add(0);
                movimientoSegundaComision.add(null);
                movimientoSegundaComision.add(null);
                movimientoSegundaComision.add(null);
                movimientoSegundaComision.add(null);
                movimientoSegundaComision.add(fechaOperacion);

                movimientosSegundaComision.add(movimientoSegundaComision);
                requestMapSegundaComision.put(KEY_MOVIMIENTOS, movimientosSegundaComision);

                Map<String, Object> responseMovimientosSegundaComision = comiciones
                        .procesarMovimientoComision(requestMapSegundaComision);

                // Agregar la respuesta de la segunda comisión al objeto final
                respuestaFinal.set("comision", responseMapper.valueToTree(responseMovimientosSegundaComision));

                // Devolver la respuesta final consolidada
                return responseMapper.writeValueAsString(respuestaFinal);
            }

            // Si no se realiza comisión, devolver el resultado del movimiento normal
            return responseMapper.writeValueAsString(respuestaFinal);

        } catch (JsonProcessingException e) {
            throw new ErrorOperacionException("Error al convertir el JSON a String", e);
        } catch (NumberFormatException e) {
            throw new ErrorOperacionException("El campo 'monto' no tiene un formato válido para double", e);
        }
    }


    private String resolverTipoAmbiente() {
        if (stpEmulacionEnabled) {
            return "SIMULADA";
        }
        boolean esProd = this.urlPagos != null && this.urlPagos.contains("prod");
        return esProd ? "PRODUCCIÓN" : "PRODUCCIÓN / DEMO";
    }

    public String enviarPagos(ObjectNode json) {
        String tipo = resolverTipoAmbiente();

        if (stpEmulacionEnabled) {
            long mockStpId = (System.currentTimeMillis() % 100000000) + 1000;
            String mockResp = String.format("{\"resultado\":{\"id\":%d,\"descripcionError\":null},\"id\":%d,\"mensaje\":\"Operacion emulada STP (MOCK)\"}", mockStpId, mockStpId);
            log.info(">>> [STP - {}] LO QUE SE MANDÓ:\n{}", tipo, json);
            log.info("<<< [STP - {}] LO QUE REGRESA STP:\n{}", tipo, mockResp);
            log.info("[STP-{}] Enviado: {} | Respuesta: {}", tipo, json, mockResp);
            return mockResp;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            String endpointUrl = this.urlPagos.trim();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ObjectNode> request = new HttpEntity<>(json, headers);

            log.info(">>> [STP - {}] LO QUE SE MANDÓ:\n{}", tipo, json);

            ResponseEntity<String> response = restTemplate.exchange(endpointUrl, HttpMethod.PUT, request, String.class);
            String responseBody = response.getBody();

            log.info("<<< [STP - {}] LO QUE REGRESA STP:\n{}", tipo, responseBody);
            log.info("[STP-{}] Enviado: {} | Respuesta: {}", tipo, json, responseBody);

            return responseBody;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("<<< [STP - {} - ERROR HTTP {}] LO QUE REGRESA STP:\n{}", tipo, e.getRawStatusCode(), errorBody);
            log.error("[STP-{}-ERROR] Status: {} | Body: {}", tipo, e.getStatusCode(), errorBody);

            if (!errorBody.isBlank()) {
                return errorBody;
            }
            return "{\"mensaje\": \"Error al validar la orden: " + e.getMessage() + "\", \"status\": " + e.getRawStatusCode() + "}";
        } catch (Exception e) {
            log.error("<<< [STP - {} - ERROR] LO QUE REGRESA STP:\n{}", tipo, e.getMessage());
            log.error("Error al enviar orden individual a STP: {}", e.getMessage(), e);
            return "{\"mensaje\": \"Error al validar la orden: " + e.getMessage() + "\", \"status\": 500}";
        }
    }

    public Map<String, Object> enviarMovimientos(Map<String, Object> request) {
        try {
            Integer ordenID = (Integer) request.get("ordenid");
            String cveCliente = (String) request.get(KEY_CVE_CLIENTE);
            String usuario = (String) request.get(KEY_USUARIO);
            String cveSucursalMov = (String) request.get(KEY_CVE_SUCURSAL);
            String cveCajaMov = (String) request.get(KEY_CVE_CAJA);
            List<List<Object>> movimientos = (List<List<Object>>) request.get(KEY_MOVIMIENTOS);

            if (cveCliente == null || usuario == null || cveSucursalMov == null || cveCajaMov == null ||
                    movimientos == null || movimientos.isEmpty()) {
                return Map.of(
                        KEY_CODIGO, 1,
                        KEY_MENSAJE, "Faltan datos obligatorios en la petición.");
            }

            String sql = "SELECT tesoreria.spi_movimientocaja(?, ?, ?, ?, ?)";

            String resultado = jdbcTemplate.execute((Connection connection) -> {
                try {
                    String[][] movimientosArray = movimientos.stream()
                            .map(list -> list.stream()
                                    .map(obj -> obj != null ? obj.toString() : null)
                                    .toArray(String[]::new))
                            .toArray(String[][]::new);
                    Array pgArray = connection.createArrayOf("text", movimientosArray);
                    return jdbcTemplate.queryForObject(sql, String.class, cveCliente, usuario, cveSucursalMov, cveCajaMov,
                            pgArray);
                } catch (SQLException e) {
                    throw new ErrorOperacionException("Error al crear el array de movimientos", e);
                }
            });

            if (resultado == null) {
                return Map.of(
                        KEY_CODIGO, 1,
                        KEY_MENSAJE, "La consulta no devolvió resultados.");
            }

            resultado = resultado.replaceAll("\\[0:5\\]=\\{|\\}", "");
            String[] valores = resultado.split(",");

            if (valores.length < 4) {
                return Map.of(
                        KEY_CODIGO, 1,
                        KEY_MENSAJE, "Respuesta inesperada del procedimiento almacenado.");
            }

            int idPoliza;
            int idMovimiento;
            try {
                idPoliza = Integer.parseInt(valores[2].trim());
                idMovimiento = Integer.parseInt(valores[3].trim());
            } catch (NumberFormatException e) {
                return Map.of(
                        KEY_CODIGO, 1,
                        KEY_MENSAJE, "Error en la conversión de valores: " + e.getMessage());
            }

            String sqlInsert = "SELECT spei.crud_ordenmovimiento(?, ?, ?)";
            String insertResultado = jdbcTemplate.execute((Connection connection) -> {
                try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
                    ps.setInt(1, ordenID);
                    ps.setInt(2, idPoliza);
                    ps.setInt(3, idMovimiento);
                    ps.execute();
                    return "Inserción exitosa";
                } catch (SQLException e) {
                    throw new ErrorOperacionException("Error al ejecutar la inserción", e);
                }
            });

            Map<String, Object> info = new HashMap<>();
            info.put("idPoliza", idPoliza);
            info.put("idMovimiento", idMovimiento);
            info.put("insertResultado", insertResultado);

            return Map.of(
                    KEY_CODIGO, 0,
                    KEY_MENSAJE, "Movimiento registrado con éxito para el cliente: " + cveCliente,
                    "info", info);

        } catch (Exception e) {
            return Map.of(
                    KEY_CODIGO, 1,
                    KEY_MENSAJE, "Error al procesar la solicitud: " + e.getMessage());
        }
    }

}
