package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class implComicionesServices {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> procesarMovimientoComision(Map<String, Object> request) {
        String cveCliente = (String) request.get("cveCliente");
        String usuario = (String) request.get("usuario");
        String cveSucursal = (String) request.get("cveSucursal");
        String cveCaja = (String) request.get("cveCaja");
        List<List<Object>> movimientos = (List<List<Object>>) request.get("movimientos");

        if (cveCliente == null || usuario == null || cveSucursal == null || cveCaja == null
                || movimientos == null || movimientos.isEmpty()) {
            return Map.of("error", "Faltan datos obligatorios en la petición.");
        }

        String[][] movimientosArray = movimientos.stream()
                .map(list -> list.stream()
                        .map(obj -> obj != null ? obj.toString() : null)
                        .toArray(String[]::new))
                .toArray(String[][]::new);

        String sql = "SELECT tesoreria.spi_movimientocaja(?, ?, ?, ?, ?)";
        String resultado = jdbcTemplate.queryForObject(sql, String.class, cveCliente, usuario, cveSucursal, cveCaja,
                movimientosArray);

        if (resultado == null) {
            return Map.of("error", "La consulta no devolvió resultados.");
        }

        resultado = resultado.replaceAll("\\[0:5\\]=\\{|\\}", "");

        return Map.of(
                "message", "Movimiento para comisión registrado con éxito para el cliente",
                "resultado", resultado);
    }

    public List<Map<String, Object>> enviarMovimientosMasivosComisiones(List<Map<String, Object>> requests) {
        List<Map<String, Object>> respuestas = new ArrayList<>();
        String sql = "SELECT tesoreria.spi_movimientocaja(?, ?, ?, ?, ?)";

        for (Map<String, Object> request : requests) {
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

            try {
                String resultado = jdbcTemplate.execute((Connection connection) -> {
                    try {
                        String[][] movimientosArray = movimientos.stream()
                                .map(list -> list.stream().map(obj -> obj != null ? obj.toString() : null)
                                        .toArray(String[]::new))
                                .toArray(String[][]::new);

                        Array pgArray = connection.createArrayOf("text", movimientosArray);
                        return jdbcTemplate.queryForObject(sql, String.class,
                                cveCliente, usuario, cveSucursal, cveCaja, pgArray);
                    } catch (SQLException e) {
                        throw new RuntimeException("Error al crear el array de movimientos", e);
                    }
                });

                respuestas.add(Map.of(
                        "cliente", cveCliente,
                        "resultado", resultado,
                        "message", "Movimiento registrado con éxito para el cliente: " + cveCliente));
            } catch (Exception e) {
                respuestas.add(Map.of(
                        "cliente", cveCliente,
                        "error", e.getMessage()));
            }
        }

        return respuestas;
    }

    public Map<String, Object> obtenerComisionesPorNumeroCliente(String numeroCliente) {
        String sql = """
                    SELECT c.id, c.comision_spei, c.estatus, c.fecha, c.idcliente,
                           c.monto_base, c.porcentaje, c.saldo_minimo
                    FROM spei.comisiones c
                    JOIN clientes.clientes cl ON cl.cliente_id = c.idcliente
                    WHERE cl.numero_cliente = ?
                """;

        try {
            List<Map<String, Object>> comisiones = jdbcTemplate.queryForList(sql, numeroCliente);

            if (comisiones == null || comisiones.isEmpty()) {
                java.util.Map<String, Object> defaultComision = new java.util.HashMap<>();
                defaultComision.put("id", 0);
                defaultComision.put("comision_spei", "0.00");
                defaultComision.put("estatus", false);
                defaultComision.put("fecha", null);
                defaultComision.put("idcliente", null);
                defaultComision.put("monto_base", "0.00");
                defaultComision.put("porcentaje", "0.00");
                defaultComision.put("saldo_minimo", "0.00");

                return Map.of(
                        "codigo", 0,
                        "mensaje", "Sin comisiones personalizadas configuradas para el cliente: " + numeroCliente,
                        "info", List.of(defaultComision));
            }

            return Map.of(
                    "codigo", 0,
                    "mensaje", "Comisiones del cliente " + numeroCliente,
                    "info", comisiones);
        } catch (Exception e) {
            return Map.of(
                    "codigo", 1,
                    "mensaje",
                    "Error al obtener comisiones para el número_cliente: " + numeroCliente + ". " + e.getMessage());
        }
    }

}
