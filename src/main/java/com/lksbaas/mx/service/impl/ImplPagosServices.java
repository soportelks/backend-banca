package com.lksbaas.mx.service.impl;

import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;
import com.lksbaas.mx.service.impl.ImplPagosServices;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
            String claveRastreo = (String) utils.generarUltimaClaveRastreo().get("nuevaClave");

            ObjectNode pagoNode = (ObjectNode) json.get("pago");
            ObjectNode clienteNode = (ObjectNode) json.get("cliente");
            boolean montoValido = saldos.validarSaldo(clienteNode);

            if (!montoValido) {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put("codigo", "1");
                respuesta.put("mensaje", "Saldo insuficiente para esta operacion");
                return mapper.writeValueAsString(respuesta);
            }

            pagoNode.put("claveRastreo", claveRastreo);
            pagoNode.put("firma", firma.firmarDatos(firma.generarCadenaFirma(pagoNode)));
            String pagoEnviado = enviarPagos(pagoNode);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode resultado = mapper.readTree(pagoEnviado);

            if (resultado.has("id") && resultado.get("id").asInt() <= 0) {
                ObjectNode respuestaError = mapper.createObjectNode();
                respuestaError.put("codigo", "1");
                respuestaError.put("mensaje",
                        resultado.has("descripcionError") ? resultado.get("descripcionError").asText()
                                : "Error desconocido al procesar el pago");
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
            root.set("datos", datosNode);

            String finalPayload = mapper.writeValueAsString(root);

            JsonNode finalJsonNode = mapper.readTree(finalPayload);
            JsonNode datosNodeFromPayload = finalJsonNode.get("datos");

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
                    respuestaError.put("codigo", "1");
                    respuestaError.put("mensaje", "Error desconocido al procesar el pago en base de datos");
                    return mapper.writeValueAsString(respuestaError);
                }
            }

            // Si la respuesta tiene código de error
            if (responseNode.has("codigo")) {
                int codigo = responseNode.get("codigo").asInt();
                if (codigo == 1) {
                    ObjectNode respuestaError = mapper.createObjectNode();
                    respuestaError.put("codigo", "1");
                    respuestaError.put("mensaje", responseNode.has("mensaje") ? responseNode.get("mensaje").asText()
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
            requestMap.put("cveCliente", clienteNode.get("datos").get("numero_cliente").asText());
            requestMap.put("usuario", usuarioMovimientos);
            requestMap.put("cveSucursal", cveSucursal);
            requestMap.put("cveCaja", cveCaja);

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
            requestMap.put("movimientos", movimientos);

            // Llamar al método enviarMovimientos y obtener el resultado
            Map<String, Object> responseMovimientos = enviarMovimientos(requestMap);

            // Validar el resultado de enviarMovimientos
            ObjectMapper responseMapper = new ObjectMapper();
            ObjectNode respuestaFinal = responseMapper.createObjectNode();

            if (responseMovimientos.containsKey("codigo")
                    && Integer.parseInt(responseMovimientos.get("codigo").toString()) == 0) {
                // Éxito
                respuestaFinal.put("codigo", "0");
                respuestaFinal.put("mensaje", responseMovimientos.get("mensaje").toString());
                respuestaFinal.set("info", responseMapper.valueToTree(responseMovimientos.get("info")));
            } else {
                // Error
                respuestaFinal.put("codigo", "1");
                respuestaFinal.put("mensaje",
                        responseMovimientos.get("mensaje") != null ? responseMovimientos.get("mensaje").toString()
                                : "Ocurrió un error al registrar los movimientos");
            }

            Map<String, Object> comisionResponse = comiciones.obtenerComisionesPorNumeroCliente(
                    clienteNode.get("datos").get("numero_cliente").asText());

            // Validar si la consulta fue exitosa
            if ((int) comisionResponse.get("codigo") != 0) {
                // Manejar el error (puedes lanzar excepción o retornar mensaje)
                throw new RuntimeException(comisionResponse.get("mensaje").toString());
            }

            // Obtener la lista de comisiones desde "info"
            List<Map<String, Object>> comisiones = (List<Map<String, Object>>) comisionResponse.get("info");

            // Aquí puedes usar la comisión que necesites, por ejemplo la primera:
            Map<String, Object> primeraComision = comisiones.get(0);
            BigDecimal comisionSpei = new BigDecimal(primeraComision.get("comision_spei").toString());
            Boolean comisionStatus = Boolean.parseBoolean(primeraComision.get("estatus").toString());
            comisionSpei = comisionSpei.add(comisionSpei.multiply(new BigDecimal("0.16")))
                    .setScale(2, RoundingMode.HALF_UP);

            // Validar si la comisión es null o igual a cero
            if (comisionSpei != null && comisionSpei.compareTo(BigDecimal.ZERO) > 0 && comisionStatus) {
                // Construir el mapa de solicitud para enviar a enviarMovimientos
                Map<String, Object> requestMapComiciones = new HashMap<>();
                requestMapComiciones.put("cveCliente", clienteNode.get("datos").get("numero_cliente").asText());
                requestMapComiciones.put("usuario", usuarioComiciones);
                requestMapComiciones.put("cveSucursal", cveSucursal);
                requestMapComiciones.put("cveCaja", cveCaja);

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
                requestMapComiciones.put("movimientos", movimientosComicion);

                comiciones.procesarMovimientoComision(requestMapComiciones);

                Map<String, Object> requestMapSegundaComision = new HashMap<>();
                requestMapSegundaComision.put("cveCliente", clientComicion.toString());
                requestMapSegundaComision.put("usuario", usuarioComiciones);
                requestMapSegundaComision.put("cveSucursal", cveSucursal);
                requestMapSegundaComision.put("cveCaja", cveCaja);

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
                requestMapSegundaComision.put("movimientos", movimientosSegundaComision);

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> enviarMovimientos(Map<String, Object> request) {
        try {
            Integer ordenID = (Integer) request.get("ordenid");
            String cveCliente = (String) request.get("cveCliente");
            String usuario = (String) request.get("usuario");
            String cveSucursal = (String) request.get("cveSucursal");
            String cveCaja = (String) request.get("cveCaja");
            List<List<Object>> movimientos = (List<List<Object>>) request.get("movimientos");

            if (cveCliente == null || usuario == null || cveSucursal == null || cveCaja == null ||
                    movimientos == null || movimientos.isEmpty()) {
                return Map.of(
                        "codigo", 1,
                        "mensaje", "Faltan datos obligatorios en la petición.");
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
                    return jdbcTemplate.queryForObject(sql, String.class, cveCliente, usuario, cveSucursal, cveCaja,
                            pgArray);
                } catch (SQLException e) {
                    throw new RuntimeException("Error al crear el array de movimientos", e);
                }
            });

            if (resultado == null) {
                return Map.of(
                        "codigo", 1,
                        "mensaje", "La consulta no devolvió resultados.");
            }

            resultado = resultado.replaceAll("\\[0:5\\]=\\{|\\}", "");
            String[] valores = resultado.split(",");

            if (valores.length < 4) {
                return Map.of(
                        "codigo", 1,
                        "mensaje", "Respuesta inesperada del procedimiento almacenado.");
            }

            int idPoliza, idMovimiento;
            try {
                idPoliza = Integer.parseInt(valores[2].trim());
                idMovimiento = Integer.parseInt(valores[3].trim());
            } catch (NumberFormatException e) {
                return Map.of(
                        "codigo", 1,
                        "mensaje", "Error en la conversión de valores: " + e.getMessage());
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
                    throw new RuntimeException("Error al ejecutar la inserción", e);
                }
            });

            Map<String, Object> info = new HashMap<>();
            info.put("idPoliza", idPoliza);
            info.put("idMovimiento", idMovimiento);
            info.put("insertResultado", insertResultado);

            return Map.of(
                    "codigo", 0,
                    "mensaje", "Movimiento registrado con éxito para el cliente: " + cveCliente,
                    "info", info);

        } catch (Exception e) {
            return Map.of(
                    "codigo", 1,
                    "mensaje", "Error al procesar la solicitud: " + e.getMessage());
        }
    }

}
