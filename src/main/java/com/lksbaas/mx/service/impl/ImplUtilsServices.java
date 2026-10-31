package com.lksbaas.mx.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ImplUtilsServices {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${zonaMexico}")
    private String zonaMexico;

    public Map<String, String> obtenerFechaOperacion() {
        ZoneId zonaHoraria = ZoneId.of(zonaMexico);
        ZonedDateTime fechaHoraActual = ZonedDateTime.now(zonaHoraria);
        LocalDate fechaActual = fechaHoraActual.toLocalDate();
        LocalTime horaActual = fechaHoraActual.toLocalTime();

        if (fechaActual.getDayOfWeek() == DayOfWeek.SATURDAY) {
            fechaActual = fechaActual.plusDays(2);
        } else if (fechaActual.getDayOfWeek() == DayOfWeek.SUNDAY) {
            fechaActual = fechaActual.plusDays(1);
        } else if (horaActual.isAfter(LocalTime.of(17, 59, 59))) {
            if (fechaActual.getDayOfWeek() == DayOfWeek.FRIDAY) {
                fechaActual = fechaActual.plusDays(3);
            } else {
                fechaActual = fechaActual.plusDays(1);
            }
        }

        String fechaFormateada = fechaActual.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return Map.of("fechaOperacion", fechaFormateada);
    }

    public Map<String, Object> generarUltimaClaveRastreo() {
        ZoneId zonaHoraria = ZoneId.of(zonaMexico);
        ZonedDateTime fechaHoraActual = ZonedDateTime.now(zonaHoraria);
        LocalDate fechaActual = fechaHoraActual.toLocalDate();
        LocalTime horaActual = fechaHoraActual.toLocalTime();

        if (fechaActual.getDayOfWeek() == DayOfWeek.SATURDAY) {
            fechaActual = fechaActual.plusDays(2);
        } else if (fechaActual.getDayOfWeek() == DayOfWeek.SUNDAY) {
            fechaActual = fechaActual.plusDays(1);
        } else if (horaActual.isAfter(LocalTime.of(17, 59, 59))) {
            if (fechaActual.getDayOfWeek() == DayOfWeek.FRIDAY) {
                fechaActual = fechaActual.plusDays(3);
            } else {
                fechaActual = fechaActual.plusDays(1);
            }
        }

        String fechaFormateada = fechaActual.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefijoBanca = "646RDAMB" + fechaFormateada;

        String sql = "SELECT claverastreo FROM spei.orden WHERE claverastreo LIKE '646RDAM%' AND fechaoperacion = ? ORDER BY id DESC LIMIT 1";
        List<String> resultados = jdbcTemplate.queryForList(sql, String.class, Integer.parseInt(fechaFormateada));

        String nuevaClave;
        String random = String.format("%04d", new Random().nextInt(9000) + 1000);

        if (!resultados.isEmpty()) {
            String ultimaClave = resultados.get(0);
            String numeroStr = ultimaClave.substring(ultimaClave.length() - 4);
            int numero = Integer.parseInt(numeroStr) + 1;

            nuevaClave = prefijoBanca + random + String.format("%04d", numero);
        } else {
            nuevaClave = prefijoBanca + random + "0001";
        }

        return Map.of("nuevaClave", nuevaClave);
    }

    public String obtenerNumeroSecuencialDesdeClave(String claveRastreo) {
        if (claveRastreo == null || claveRastreo.length() < 4) {
            throw new IllegalArgumentException("Clave de rastreo inválida");
        }

        String ultimosCuatro = claveRastreo.substring(claveRastreo.length() - 4);
        return String.valueOf(Integer.parseInt(ultimosCuatro)); // Elimina ceros a la izquierda
    }

}
