package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ImplTraspasosServices {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> procesarMovimientoTraspaso(Map<String, Object> request) {
        String cveCliente = Objects.requireNonNullElse(request.get("cveCliente"), "").toString();
        String usuario = Objects.requireNonNullElse(request.get("usuario"), "").toString();
        String cveSucursal = Objects.requireNonNullElse(request.get("cveSucursal"), "").toString();
        String cveCaja = Objects.requireNonNullElse(request.get("cveCaja"), "").toString();
        List<List<Object>> movimientos = (List<List<Object>>) request.get("movimientos");

        if (cveCliente.isEmpty() || usuario.isEmpty() || cveSucursal.isEmpty() || cveCaja.isEmpty()
                || movimientos == null || movimientos.isEmpty()) {
            return Map.of("error", "Faltan datos obligatorios en la petición.");
        }

        String[][] movimientosArray = movimientos.stream()
                .map(list -> list.stream().map(obj -> obj != null ? obj.toString() : "")
                        .toArray(String[]::new))
                .toArray(String[][]::new);

        String resultado = jdbcTemplate.execute((Connection connection) -> {
            try {
                Array pgArray = connection.createArrayOf("text", movimientosArray);
                return jdbcTemplate.queryForObject(
                        "SELECT tesoreria.spi_movimientocaja(?, ?, ?, ?, ?)",
                        String.class, cveCliente, usuario, cveSucursal, cveCaja, pgArray);
            } catch (SQLException e) {
                throw new RuntimeException("Error al procesar la consulta SQL", e);
            }
        });

        if (resultado == null || resultado.trim().isEmpty()) {
            return Map.of("error", "La consulta no devolvió resultados.");
        }

        resultado = resultado.replaceAll("\\[\\d+:\\d+\\]=\\{|\\}", "").trim();
        return Map.of("resultado", resultado);
    }

}
