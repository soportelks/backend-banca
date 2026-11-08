package com.lksbaas.mx.service.impl;

import com.lksbaas.mx.exception.ErrorOperacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImplPagosMasivosServices {
    private static final String KEY_DATOS = "datos";
    private static final String KEY_ERROR = "error";
    private static final String KEY_MONTO = "monto";
    private static final String KEY_CODIGO = "codigo";
    private static final String KEY_ESTADO = "estado";
    private static final String KEY_CLIENTE = "cliente";
    private static final String KEY_CVE_CAJA = "cveCaja";
    private static final String KEY_ESTATUS = "estatus";
    private static final String KEY_MENSAJE = "mensaje";
    private static final String KEY_USUARIO = "usuario";
    private static final String KEY_BLOQUEADO = "bloqueado";
    private static final String KEY_RESULTADO = "resultado";
    private static final String KEY_CVE_CLIENTE = "cveCliente";
    private static final String KEY_CVE_SUCURSAL = "cveSucursal";
    private static final String KEY_MOVIMIENTOS = "movimientos";
    private static final String KEY_FECHA_OPERACION = "fechaOperacion";
    private static final String KEY_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String KEY_NUMERO_CLIENTE = "numero_cliente";
    private static final String KEY_CUENTA_ORDENANTE = "cuentaOrdenante";
    private static final String KEY_DESCRIPCION_ERROR = "descripcionError";
    private static final String KEY_MINUTOS_RESTANTES = "minutosRestantes";
    private static final String KEY_SEGUNDOS_RESTANTES = "segundosRestantes";
    private static final String TXT_NO_FUE_POSIBLE_VALIDAR_EL_CO = "No fue posible validar el control de duplicados. Intenta nuevamente.";


    private static final Logger log = LoggerFactory.getLogger(ImplPagosMasivosServices.class);

    private final IDinamicoService service;

    private final ImplUtilsServices utils;

    private final ImplSaldoServices saldos;

    private final ImplFirmaServices firma;

    private final JdbcTemplate jdbcTemplate;

    private final ImplComicionesServices comiciones;

    public ImplPagosMasivosServices(IDinamicoService service, ImplUtilsServices utils, ImplSaldoServices saldos, ImplFirmaServices firma, JdbcTemplate jdbcTemplate, ImplComicionesServices comiciones) {
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
            String fechaOperacion = utils.obtenerFechaOperacion().get(KEY_FECHA_OPERACION);

            ObjectNode clienteNode = (ObjectNode) json.get(KEY_CLIENTE);
            String numeroCliente = clienteNode.get(KEY_DATOS).get(KEY_NUMERO_CLIENTE).asText();

            ArrayNode pagosArray = (ArrayNode) json.get("pago");
            if (pagosArray == null || pagosArray.isEmpty()) {
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put(KEY_CODIGO, "1");
                respuesta.put(KEY_MENSAJE, "La lista de pagos se encuentra vacía");
                return mapper.writeValueAsString(respuesta);
            }

            // Obtener cuenta ordenante del primer pago si está disponible
            String cuentaOrdenante = "";
            if (pagosArray.get(0).hasNonNull(KEY_CUENTA_ORDENANTE)) {
                cuentaOrdenante = pagosArray.get(0).get(KEY_CUENTA_ORDENANTE).asText().trim();
            }

            // 1. Obtener o calcular clave de idempotencia
            if (json.hasNonNull(KEY_IDEMPOTENCY_KEY) && !json.get(KEY_IDEMPOTENCY_KEY).asText().isBlank()) {
                idempotencyKey = json.get(KEY_IDEMPOTENCY_KEY).asText().trim();
            } else {
                long hashVal = json.toString().hashCode();
                idempotencyKey = "LOTE_" + numeroCliente + "_" + fechaOperacion + "_" + pagosArray.size() + "_" + Math.abs(hashVal);
            }

            // 2. Calcular suma total exacta con BigDecimal
            BigDecimal sumaTotalMontos = BigDecimal.ZERO;
            for (JsonNode pago : pagosArray) {
                if (pago.hasNonNull(KEY_MONTO)) {
                    sumaTotalMontos = sumaTotalMontos.add(new BigDecimal(pago.get(KEY_MONTO).asText().trim()));
                }
            }

            // 3. VALIDACIÓN DE SEGURIDAD: COOLDOWN DE 10 MINUTOS
            // Se valida si este cliente o cuenta ya envió una dispersión en los últimos 10 minutos.
            // Si intenta enviar otra antes de 10 min, se bloquea y se envía el error al front.
            Map<String, Object> cooldownCheck = verificarCooldownDispersion(numeroCliente, cuentaOrdenante, idempotencyKey);
            if ((boolean) cooldownCheck.getOrDefault(KEY_BLOQUEADO, false)) {
                log.warn("Dispersión masiva bloqueada por cooldown de 10 min para cliente {}. Faltan {} seg.",
                        numeroCliente, cooldownCheck.get(KEY_SEGUNDOS_RESTANTES));
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put(KEY_CODIGO, "1");
                respuesta.put(KEY_ESTADO, "BLOQUEADO_POR_TIEMPO");
                respuesta.put(KEY_IDEMPOTENCY_KEY, idempotencyKey);
                respuesta.put(KEY_SEGUNDOS_RESTANTES, ((Number) cooldownCheck.get(KEY_SEGUNDOS_RESTANTES)).longValue());
                respuesta.put(KEY_MINUTOS_RESTANTES, ((Number) cooldownCheck.get(KEY_MINUTOS_RESTANTES)).longValue());
                respuesta.put(KEY_MENSAJE, (String) cooldownCheck.get(KEY_MENSAJE));
                return mapper.writeValueAsString(respuesta);
            }

            // 4. Reclamar el lote de forma atómica en spei.lote_dispersion. Solo un proceso puede tomarlo.
            boolean loteReclamado;
            try {
                int insertados = jdbcTemplate.update("""
                    INSERT INTO spei.lote_dispersion (idempotency_key, cve_cliente, total_ordenes, monto_total, estatus, fecha_creacion, fecha_actualizacion)
                    VALUES (?, ?, ?, ?, 'PROCESANDO', NOW(), NOW())
                    ON CONFLICT (idempotency_key) DO NOTHING
                """, idempotencyKey, numeroCliente, pagosArray.size(), sumaTotalMontos);
                loteReclamado = insertados > 0;
            } catch (Exception e) {
                log.error("No fue posible registrar el control de idempotencia del lote {}: {}", idempotencyKey, e.getMessage());
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put(KEY_CODIGO, "1");
                respuesta.put(KEY_MENSAJE, TXT_NO_FUE_POSIBLE_VALIDAR_EL_CO);
                return mapper.writeValueAsString(respuesta);
            }

            // Si el lote ya existe, bloquear o devolver el resultado previo sin volver a dispersar
            if (!loteReclamado) {
                try {
                    List<Map<String, Object>> lotes = jdbcTemplate.queryForList(
                            "SELECT id, estatus, respuesta_json FROM spei.lote_dispersion WHERE idempotency_key = ?",
                            idempotencyKey);

                    if (lotes.isEmpty()) {
                        ObjectNode respuesta = mapper.createObjectNode();
                        respuesta.put(KEY_CODIGO, "1");
                        respuesta.put(KEY_MENSAJE, TXT_NO_FUE_POSIBLE_VALIDAR_EL_CO);
                        return mapper.writeValueAsString(respuesta);
                    }

                    Map<String, Object> lote = lotes.get(0);
                    String estatus = String.valueOf(lote.get(KEY_ESTATUS));

                    if ("PROCESANDO".equalsIgnoreCase(estatus)) {
                        log.warn("Intento de doble dispersión bloqueado para el lote idempotente: {}", idempotencyKey);
                        ObjectNode respuesta = mapper.createObjectNode();
                        respuesta.put(KEY_CODIGO, "1");
                        respuesta.put(KEY_ESTADO, "EN_PROCESO");
                        respuesta.put(KEY_IDEMPOTENCY_KEY, idempotencyKey);
                        respuesta.put(KEY_MENSAJE, "Este lote de nómina ya está en proceso de dispersión. Espera a que termine; no se generaron órdenes nuevas.");
                        return mapper.writeValueAsString(respuesta);
                    }

                    if ("COMPLETADO".equalsIgnoreCase(estatus)) {
                        log.info("Lote {} ya completado previamente. Retornando respuesta guardada.", idempotencyKey);
                        String respuestaPrevia = (String) lote.get("respuesta_json");
                        if (respuestaPrevia != null && !respuestaPrevia.isBlank()) {
                            ObjectNode respuesta = (ObjectNode) mapper.readTree(respuestaPrevia);
                            respuesta.put("duplicado", true);
                            respuesta.put("loteId", String.valueOf(lote.get("id")));
                            respuesta.put(KEY_IDEMPOTENCY_KEY, idempotencyKey);
                            respuesta.put(KEY_MENSAJE, "Este lote ya había sido procesado previamente. No se generaron órdenes nuevas.");
                            return mapper.writeValueAsString(respuesta);
                        }
                        ObjectNode respuesta = mapper.createObjectNode();
                        respuesta.put(KEY_CODIGO, "1");
                        respuesta.put(KEY_ESTADO, "COMPLETADO");
                        respuesta.put(KEY_IDEMPOTENCY_KEY, idempotencyKey);
                        respuesta.put(KEY_MENSAJE, "Este lote ya fue procesado previamente. No se generaron órdenes nuevas.");
                        return mapper.writeValueAsString(respuesta);
                    }

                    ObjectNode respuesta = mapper.createObjectNode();
                    respuesta.put(KEY_CODIGO, "1");
                    respuesta.put(KEY_ESTADO, estatus);
                    respuesta.put(KEY_IDEMPOTENCY_KEY, idempotencyKey);
                    if ("FALLIDO".equalsIgnoreCase(estatus)) {
                        respuesta.put(KEY_MENSAJE, "Este lote quedó con estatus FALLIDO. Por seguridad no se reintenta automáticamente; revisa el estado de las órdenes en el proveedor antes de generar un nuevo lote.");
                    } else {
                        respuesta.put(KEY_MENSAJE, "Este lote de nómina ya fue registrado con estatus " + estatus + ".");
                    }
                    return mapper.writeValueAsString(respuesta);
                } catch (JsonProcessingException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Error al consultar el control de idempotencia del lote {}: {}", idempotencyKey, e.getMessage());
                    ObjectNode respuesta = mapper.createObjectNode();
                    respuesta.put(KEY_CODIGO, "1");
                    respuesta.put(KEY_MENSAJE, TXT_NO_FUE_POSIBLE_VALIDAR_EL_CO);
                    return mapper.writeValueAsString(respuesta);
                }
            }

            // 5. REGISTRAR DE INMEDIATO EN LA TABLA DE SEGURIDAD (AL VALIDAR EL OTP)
            // Se activa desde el momento en que se procesa el OTP (inicio de la dispersión)
            // y no hasta que concluyan todos los pagos.
            registrarInicioCooldown(numeroCliente, cuentaOrdenante, idempotencyKey, pagosArray.size(), sumaTotalMontos);

            // 6. Validar saldo disponible del cliente
            boolean montoValido = saldos.validarSaldo(clienteNode);
            if (!montoValido) {
                liberarLote(idempotencyKey);
                liberarCooldownPorSaldo(idempotencyKey);
                ObjectNode respuesta = mapper.createObjectNode();
                respuesta.put(KEY_CODIGO, "1");
                respuesta.put(KEY_MENSAJE, "Saldo insuficiente para esta operacion");
                return mapper.writeValueAsString(respuesta);
            }

            // 6. Consultar comisiones del cliente UNA sola vez antes del ciclo
            BigDecimal comisionSpei = BigDecimal.ZERO;
            boolean aplicaComision = false;
            try {
                Map<String, Object> comisionResponse = comiciones.obtenerComisionesPorNumeroCliente(numeroCliente);
                if (comisionResponse != null && (int) comisionResponse.get(KEY_CODIGO) == 0) {
                    List<Map<String, Object>> comisionesListBD = (List<Map<String, Object>>) comisionResponse.get("info");
                    if (comisionesListBD != null && !comisionesListBD.isEmpty()) {
                        Map<String, Object> primeraComision = comisionesListBD.get(0);
                        Object comisionValue = primeraComision.get("comision_spei");
                        boolean comisionStatus = primeraComision.get(KEY_ESTATUS) != null && Boolean.parseBoolean(primeraComision.get(KEY_ESTATUS).toString());

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

                // Regla STP: Solo se considera exitoso si STP retorna un ID positivo (> 0).
                // Si el ID es negativo (<= 0, ej. -53), nulo o viene con error, es rechazo de STP
                // y se contabiliza como fallido sin descontar saldo ni registrarse.
                boolean pagoValido = false;
                long stpId = -1;
                String errorDesc = null;

                if (resultado != null) {
                    if (resultado.has(KEY_RESULTADO) && resultado.get(KEY_RESULTADO).isObject()) {
                        JsonNode resObj = resultado.get(KEY_RESULTADO);
                        if (resObj.has("id") && !resObj.get("id").isNull()) {
                            stpId = resObj.get("id").asLong();
                        }
                        if (resObj.hasNonNull(KEY_DESCRIPCION_ERROR)) {
                            errorDesc = resObj.get(KEY_DESCRIPCION_ERROR).asText();
                        }
                    }
                    if (stpId <= 0 && resultado.has("id") && !resultado.get("id").isNull()) {
                        stpId = resultado.get("id").asLong();
                    }
                    if ((errorDesc == null || errorDesc.isBlank()) && resultado.hasNonNull(KEY_DESCRIPCION_ERROR)) {
                        errorDesc = resultado.get(KEY_DESCRIPCION_ERROR).asText();
                    }
                    if ((errorDesc == null || errorDesc.isBlank()) && resultado.hasNonNull(KEY_MENSAJE)) {
                        errorDesc = resultado.get(KEY_MENSAJE).asText();
                    }
                    if ((errorDesc == null || errorDesc.isBlank()) && resultado.hasNonNull(KEY_ERROR)) {
                        errorDesc = resultado.get(KEY_ERROR).asText();
                    }
                }

                if (stpId > 0) {
                    pagoValido = true;
                }

                if (!pagoValido) {
                    fallidos++;
                    String causa;
                    if (errorDesc != null && !errorDesc.isBlank()) {
                        causa = errorDesc;
                    } else {
                        causa = "STP rechazó la operación" + (stpId != -1 ? " (ID: " + stpId + ")" : "");
                    }
                    causasFallos.add(causa);
                    if (log.isWarnEnabled()) {
                        log.warn("[STP RECHAZO EN DISPERSIÓN MASIVA] Beneficiario: {} | stpId: {} | Causa: {}",
                                pagoNode.path("nombreBeneficiario").asText(), stpId, causa);
                    }
                    continue;
                }

                pagoNode.put(KEY_FECHA_OPERACION, fechaOperacion);
                pagoNode.put("folioOrigen", utils.obtenerNumeroSecuencialDesdeClave(claveRastreoPago));

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

                if (!responseNode.isObject()) {
                    if (responseNode.isArray() && responseNode.size() > 0) {
                        responseNode = responseNode.get(0);
                    } else {
                        fallidos++;
                        causasFallos.add("Error desconocido al procesar el pago en base de datos");
                        continue;
                    }
                }

                if (responseNode.has(KEY_CODIGO) && responseNode.get(KEY_CODIGO).asInt() == 1) {
                    fallidos++;
                    causasFallos.add(responseNode.has(KEY_MENSAJE)
                            ? responseNode.get(KEY_MENSAJE).asText()
                            : "Error al registrar el pago");
                    continue;
                }

                int ordenID = responseNode.get("id").asInt();
                Double monto = responseNode.get(KEY_MONTO).asDouble();

                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("ordenid", ordenID);
                requestMap.put(KEY_CVE_CLIENTE, numeroCliente);
                requestMap.put(KEY_USUARIO, usuarioMovimientos);
                requestMap.put(KEY_CVE_SUCURSAL, cveSucursal);
                requestMap.put(KEY_CVE_CAJA, cveCaja);

                List<Object> movimiento = Arrays.asList(
                        "03", "02", "0002", monto, null, 0,
                        null, null, null, null, fechaOperacion);

                requestMap.put(KEY_MOVIMIENTOS, List.of(movimiento));
                movimientosList.add(requestMap);

                if (aplicaComision) {
                    Map<String, Object> requestMapComisiones = new HashMap<>();
                    requestMapComisiones.put(KEY_CVE_CLIENTE, numeroCliente);
                    requestMapComisiones.put(KEY_USUARIO, usuarioComiciones);
                    requestMapComisiones.put(KEY_CVE_SUCURSAL, cveSucursal);
                    requestMapComisiones.put(KEY_CVE_CAJA, cveCaja);

                    List<Object> movimientoComision = Arrays.asList(
                            "03", "02", "0002", comisionSpei, null, 0,
                            null, null, null, null, fechaOperacion);

                    requestMapComisiones.put(KEY_MOVIMIENTOS, List.of(movimientoComision));
                    comisionesList.add(requestMapComisiones);

                    Map<String, Object> requestMapDepocito = new HashMap<>();
                    requestMapDepocito.put(KEY_CVE_CLIENTE, clientComicion);
                    requestMapDepocito.put(KEY_USUARIO, usuarioComiciones);
                    requestMapDepocito.put(KEY_CVE_SUCURSAL, cveSucursal);
                    requestMapDepocito.put(KEY_CVE_CAJA, cveCaja);

                    List<Object> movimientoDepocito = Arrays.asList(
                            "03", "01", "0002", comisionSpei, null, 0,
                            null, null, null, null, fechaOperacion);

                    requestMapDepocito.put(KEY_MOVIMIENTOS, List.of(movimientoDepocito));
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
            resultadoFinal.put(KEY_CODIGO, "0");
            resultadoFinal.put(KEY_MENSAJE, "Procesamiento completado");
            resultadoFinal.put("exitosos", exitosos);
            resultadoFinal.put("fallidos", fallidos);
            resultadoFinal.set("causasFallos", mapper.valueToTree(causasFallos));
            resultadoFinal.put(KEY_IDEMPOTENCY_KEY, idempotencyKey);
            resultadoFinal.put("totalOrdenes", pagosArray.size());
            resultadoFinal.put("montoTotal", sumaTotalMontos);
            resultadoFinal.put(KEY_FECHA_OPERACION, fechaOperacion);
            resultadoFinal.put("duplicado", false);

            String respuestaStr = mapper.writeValueAsString(resultadoFinal);

            // 9. Actualizar lote como COMPLETADO
            actualizarLoteCompletado(respuestaStr, idempotencyKey);

            actualizarCooldownCompletado(idempotencyKey);

            return respuestaStr;

        } catch (JsonProcessingException e) {
            marcarLoteFallido(idempotencyKey);
            marcarCooldownFallido(idempotencyKey);
            throw new ErrorOperacionException("Error al procesar el JSON de la nómina masiva", e);
        } catch (NumberFormatException e) {
            marcarLoteFallido(idempotencyKey);
            marcarCooldownFallido(idempotencyKey);
            throw new ErrorOperacionException("Formato numérico inválido en montos de nómina", e);
        } catch (Exception e) {
            marcarLoteFallido(idempotencyKey);
            marcarCooldownFallido(idempotencyKey);
            log.error("Error durante la dispersión masiva: {}", e.getMessage(), e);
            throw new ErrorOperacionException("Error al ejecutar la dispersión masiva: " + e.getMessage(), e);
        }
    }

    private void actualizarLoteCompletado(String respuestaStr, String idempotencyKey) {
        try {
            jdbcTemplate.update(
                    "UPDATE spei.lote_dispersion SET estatus = 'COMPLETADO', respuesta_json = ?, fecha_actualizacion = NOW() WHERE idempotency_key = ?",
                    respuestaStr, idempotencyKey);
        } catch (Exception e) {
            log.warn("No se pudo actualizar estado COMPLETADO en lote_dispersion: {}", e.getMessage());
        }
    }

    private void liberarLote(String idempotencyKey) {
        if (idempotencyKey != null) {
            try {
                jdbcTemplate.update(
                        "DELETE FROM spei.lote_dispersion WHERE idempotency_key = ? AND estatus = 'PROCESANDO'",
                        idempotencyKey);
            } catch (Exception ex) {
                log.warn("No se pudo liberar el lote {}: {}", idempotencyKey, ex.getMessage());
            }
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

    private volatile boolean tablaVerificada = false;

    private void asegurarTablaControl() {
        if (!tablaVerificada) {
            synchronized (this) {
                if (!tablaVerificada) {
                    try {
                        jdbcTemplate.execute("""
                            CREATE SCHEMA IF NOT EXISTS spei;
                            CREATE TABLE IF NOT EXISTS spei.control_bloqueo_dispersion (
                                id BIGSERIAL PRIMARY KEY,
                                cve_cliente VARCHAR(50) NOT NULL,
                                cuenta_ordenante VARCHAR(30),
                                idempotency_key VARCHAR(120),
                                total_ordenes INT,
                                monto_total NUMERIC(16,2),
                                fecha_solicitud TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
                                estatus VARCHAR(30) DEFAULT 'INICIADO'
                            );
                            CREATE INDEX IF NOT EXISTS idx_bloqueo_disp_cliente_fecha ON spei.control_bloqueo_dispersion(cve_cliente, fecha_solicitud DESC);
                            CREATE INDEX IF NOT EXISTS idx_bloqueo_disp_cuenta_fecha ON spei.control_bloqueo_dispersion(cuenta_ordenante, fecha_solicitud DESC);
                        """);
                        tablaVerificada = true;
                    } catch (Exception e) {
                        log.warn("Aviso al asegurar tabla spei.control_bloqueo_dispersion: {}", e.getMessage());
                    }
                }
            }
        }
    }

    public Map<String, Object> verificarCooldownDispersion(String numeroCliente, String cuentaOrdenante, String idempotencyKey) {
        asegurarTablaControl();

        Map<String, Object> resultado = new HashMap<>();
        resultado.put(KEY_BLOQUEADO, false);
        resultado.put(KEY_SEGUNDOS_RESTANTES, 0L);
        resultado.put(KEY_MINUTOS_RESTANTES, 0L);
        resultado.put(KEY_MENSAJE, "");

        if (numeroCliente == null || numeroCliente.isBlank()) {
            return resultado;
        }

        try {
            String sql = """
                SELECT idempotency_key, fecha_solicitud, estatus,
                       EXTRACT(EPOCH FROM (fecha_solicitud + INTERVAL '10 minutes' - LOCALTIMESTAMP)) AS segundos_restantes
                FROM spei.control_bloqueo_dispersion
                WHERE (cve_cliente = ? OR (cuenta_ordenante IS NOT NULL AND cuenta_ordenante != '' AND cuenta_ordenante = ?))
                  AND estatus != 'CANCELADO_SALDO'
                  AND fecha_solicitud >= LOCALTIMESTAMP - INTERVAL '10 minutes'
                ORDER BY fecha_solicitud DESC
                LIMIT 1
            """;

            List<Map<String, Object>> registros = jdbcTemplate.queryForList(
                    sql,
                    numeroCliente.trim(),
                    cuentaOrdenante != null ? cuentaOrdenante.trim() : ""
            );

            if (!registros.isEmpty()) {
                Map<String, Object> reg = registros.get(0);
                String claveReg = (String) reg.get("idempotency_key");
                Number segs = (Number) reg.get("segundos_restantes");
                long segundosRestantes = segs != null ? Math.max(0, segs.longValue()) : 0;

                if (segundosRestantes > 0) {
                    if (idempotencyKey != null && idempotencyKey.equalsIgnoreCase(claveReg)) {
                        resultado.put(KEY_BLOQUEADO, true);
                        resultado.put("mismoLote", true);
                        resultado.put(KEY_SEGUNDOS_RESTANTES, segundosRestantes);
                        resultado.put(KEY_MINUTOS_RESTANTES, (long) Math.ceil(segundosRestantes / 60.0));
                        resultado.put(KEY_MENSAJE, "Este lote de dispersión ya fue enviado y está siendo procesado. Por seguridad, debes esperar 10 minutos para enviar un lote diferente.");
                        return resultado;
                    }

                    long minutos = segundosRestantes / 60;
                    long segundos = segundosRestantes % 60;

                    StringBuilder tiempoTexto = new StringBuilder();
                    if (minutos > 0) {
                        tiempoTexto.append(minutos).append(minutos == 1 ? " minuto" : " minutos");
                        if (segundos > 0) {
                            tiempoTexto.append(" y ").append(segundos).append(segundos == 1 ? " segundo" : " segundos");
                        }
                    } else {
                        tiempoTexto.append(segundos).append(segundos == 1 ? " segundo" : " segundos");
                    }

                    resultado.put(KEY_BLOQUEADO, true);
                    resultado.put("mismoLote", false);
                    resultado.put(KEY_SEGUNDOS_RESTANTES, segundosRestantes);
                    resultado.put(KEY_MINUTOS_RESTANTES, (long) Math.ceil(segundosRestantes / 60.0));
                    resultado.put(KEY_MENSAJE, "Por motivos de seguridad, debes esperar 10 minutos entre cada dispersión masiva. Podrás enviar una nueva dispersión en " + tiempoTexto + ".");
                    return resultado;
                }
            }
        } catch (Exception e) {
            log.error("Error al verificar cooldown de dispersión masiva: {}", e.getMessage(), e);
        }

        return resultado;
    }

    private void registrarInicioCooldown(String numeroCliente, String cuentaOrdenante, String idempotencyKey, int totalOrdenes, BigDecimal montoTotal) {
        asegurarTablaControl();
        try {
            jdbcTemplate.update("""
                INSERT INTO spei.control_bloqueo_dispersion (
                    cve_cliente, cuenta_ordenante, idempotency_key, total_ordenes, monto_total, fecha_solicitud, estatus
                ) VALUES (?, ?, ?, ?, ?, LOCALTIMESTAMP, 'INICIADO')
            """, numeroCliente, cuentaOrdenante, idempotencyKey, totalOrdenes, montoTotal);
            log.info("Cooldown de seguridad de 10 min activado para cliente {} (lote: {})", numeroCliente, idempotencyKey);
        } catch (Exception e) {
            log.error("No se pudo registrar inicio de cooldown en control_bloqueo_dispersion: {}", e.getMessage());
        }
    }

    private void actualizarCooldownCompletado(String idempotencyKey) {
        if (idempotencyKey != null) {
            try {
                jdbcTemplate.update(
                        "UPDATE spei.control_bloqueo_dispersion SET estatus = 'COMPLETADO' WHERE idempotency_key = ?",
                        idempotencyKey);
            } catch (Exception e) {
                log.warn("No se pudo actualizar estatus COMPLETADO en control_bloqueo_dispersion: {}", e.getMessage());
            }
        }
    }

    private void liberarCooldownPorSaldo(String idempotencyKey) {
        if (idempotencyKey != null) {
            try {
                jdbcTemplate.update(
                        "UPDATE spei.control_bloqueo_dispersion SET estatus = 'CANCELADO_SALDO' WHERE idempotency_key = ?",
                        idempotencyKey);
            } catch (Exception e) {
                log.warn("No se pudo cancelar cooldown por saldo insuficiente: {}", e.getMessage());
            }
        }
    }

    private void marcarCooldownFallido(String idempotencyKey) {
        if (idempotencyKey != null) {
            try {
                jdbcTemplate.update(
                        "UPDATE spei.control_bloqueo_dispersion SET estatus = 'FALLIDO' WHERE idempotency_key = ?",
                        idempotencyKey);
            } catch (Exception e) {
                log.warn("No se pudo marcar cooldown como FALLIDO: {}", e.getMessage());
            }
        }
    }

    public String consultarCooldown(ObjectNode json) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode respuesta = mapper.createObjectNode();

        String numeroCliente = "";
        String cuentaOrdenante = "";

        if (json.hasNonNull("numeroCliente")) {
            numeroCliente = json.get("numeroCliente").asText().trim();
        } else if (json.hasNonNull(KEY_CLIENTE) && json.get(KEY_CLIENTE).hasNonNull(KEY_DATOS) && json.get(KEY_CLIENTE).get(KEY_DATOS).hasNonNull(KEY_NUMERO_CLIENTE)) {
            numeroCliente = json.get(KEY_CLIENTE).get(KEY_DATOS).get(KEY_NUMERO_CLIENTE).asText().trim();
        }

        if (json.hasNonNull(KEY_CUENTA_ORDENANTE)) {
            cuentaOrdenante = json.get(KEY_CUENTA_ORDENANTE).asText().trim();
        }

        Map<String, Object> check = verificarCooldownDispersion(numeroCliente, cuentaOrdenante, null);
        boolean bloqueado = (boolean) check.getOrDefault(KEY_BLOQUEADO, false);

        respuesta.put(KEY_BLOQUEADO, bloqueado);
        respuesta.put(KEY_SEGUNDOS_RESTANTES, ((Number) check.getOrDefault(KEY_SEGUNDOS_RESTANTES, 0L)).longValue());
        respuesta.put(KEY_MINUTOS_RESTANTES, ((Number) check.getOrDefault(KEY_MINUTOS_RESTANTES, 0L)).longValue());
        respuesta.put(KEY_MENSAJE, (String) check.getOrDefault(KEY_MENSAJE, bloqueado ? "Dispersión bloqueada por tiempo" : "Sin restricciones activas"));

        try {
            return mapper.writeValueAsString(respuesta);
        } catch (Exception e) {
            return "{\"bloqueado\":" + bloqueado + ",\"mensaje\":\"" + respuesta.get(KEY_MENSAJE).asText() + "\"}";
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
            return "{\"mensaje\": \"Error al validar la orden en STP: " + e.getMessage() + "\", \"status\": " + e.getRawStatusCode() + "}";
        } catch (Exception e) {
            log.error("<<< [STP - {} - ERROR] LO QUE REGRESA STP:\n{}", tipo, e.getMessage());
            log.error("Error al enviar orden a STP: {}", e.getMessage(), e);
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
                String cveCliente = (String) request.get(KEY_CVE_CLIENTE);
                String usuario = (String) request.get(KEY_USUARIO);
                String cveSucursalMov = (String) request.get(KEY_CVE_SUCURSAL);
                String cveCajaMov = (String) request.get(KEY_CVE_CAJA);
                List<List<Object>> movimientos = (List<List<Object>>) request.get(KEY_MOVIMIENTOS);

                if (cveCliente == null || usuario == null || cveSucursalMov == null || cveCajaMov == null
                        || movimientos == null || movimientos.isEmpty()) {
                    respuestas.add(Map.of(KEY_ERROR, "Faltan datos obligatorios en la petición."));
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
                        ps.setString(3, cveSucursalMov);
                        ps.setString(4, cveCajaMov);
                        ps.setArray(5, pgArray);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? rs.getString(1) : null;
                        }
                    } finally {
                        pgArray.free();
                    }
                });

                if (resultado == null) {
                    respuestas.add(Map.of(KEY_ERROR, "La consulta no devolvió resultados."));
                    continue;
                }

                resultado = resultado.replaceAll("\\[\\d+:\\d+\\]=\\{|\\{|\\}", "");
                String[] valores = resultado.split(",");

                for (int i = 0; i < valores.length; i++) {
                    valores[i] = valores[i].trim();
                    if (i != 1) {
                        valores[i] = quitarComillasExtremos(valores[i]);
                    }
                }

                if (valores.length < 4) {
                    respuestas.add(Map.of(KEY_ERROR, "Respuesta inesperada del procedimiento almacenado."));
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
                respuestas.add(Map.of(KEY_ERROR, "Error interno: " + e.getMessage()));
            }
        }

        return respuestas;
    }

    private static String quitarComillasExtremos(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int start = 0;
        int end = s.length();
        if (s.startsWith("\"")) {
            start++;
        }
        if (end > start && s.endsWith("\"")) {
            end--;
        }
        return s.substring(start, end);
    }
}
