package com.lksbaas.mx.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.service.IDinamicoService;

@RestController
@CrossOrigin("*")
@RequestMapping("/catalogo")
public class CatalogosController {

	private static final String JSON_PROD_CONS = "application/json;charset=UTF-8";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${zonaMexico}")
	private String zonaMexico;

	/*
	 * Query para consultar los generales de cada categoria
	 * 
	 * @param cve_categoria = String(TEXT)
	 */
	@Value("${query.catalogos.sps_genelares_por_categoria}")
	private String querySpsGeneralesCategoria;

	// Query para consulta de productos y servicios
	@Value("${query.catalogos.sps_productos_servicios}")
	private String querySPSProductosServicios;

	/**
	 * Instancia al service dinamico
	 */
	@Autowired
	private IDinamicoService serviceCatalogo;

	@PostMapping(path = "/get-generales-categoria", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsGeneralesCategoria(@RequestBody Request request) {
		return serviceCatalogo.execQueryDinamico(request, querySpsGeneralesCategoria);
	}

	/**
	 * Metodo para obtener los datos de productos y servicios
	 */
	@PostMapping(path = "/get-productos-servicios", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsProductosServicios(@RequestBody Request request) {
		return serviceCatalogo.execQueryDinamico(request, querySPSProductosServicios);
	}

	@GetMapping("/fecha-operacion")
	public ResponseEntity<Map<String, String>> getFechaOperacion() {
		try {
			// Definir la zona horaria de Ciudad de México
			ZoneId zonaHoraria = ZoneId.of(zonaMexico);

			// Obtener fecha y hora en la zona configurada
			ZonedDateTime fechaHoraActual = ZonedDateTime.now(zonaHoraria);
			LocalDate fechaActual = fechaHoraActual.toLocalDate();
			LocalTime horaActual = fechaHoraActual.toLocalTime();

			// Si es sábado o domingo, avanzar al lunes
			if (fechaActual.getDayOfWeek() == DayOfWeek.SATURDAY) {
				fechaActual = fechaActual.plusDays(2);
			} else if (fechaActual.getDayOfWeek() == DayOfWeek.SUNDAY) {
				fechaActual = fechaActual.plusDays(1);
			} else if (horaActual.isAfter(LocalTime.of(17, 59, 59)) && fechaActual.getDayOfWeek() == DayOfWeek.FRIDAY) {
				fechaActual = fechaActual.plusDays(3); // Salta directamente al lunes
			} else if (horaActual.isAfter(LocalTime.of(17, 59, 59))) {
				fechaActual = fechaActual.plusDays(1); // Caso normal
			}

			// Formatear la fecha como YYYYMMDD
			String fechaFormateada = fechaActual.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

			return ResponseEntity.ok(Map.of("fechaOperacion", fechaFormateada));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping("/ultima-clave-rastreo")
	public ResponseEntity<Map<String, Object>> getUltimaClaveRastreo() {
		try {
			// Definir la zona horaria de Ciudad de México
			ZoneId zonaHoraria = ZoneId.of(zonaMexico);

			// Obtener fecha y hora en la zona configurada
			ZonedDateTime fechaHoraActual = ZonedDateTime.now(zonaHoraria);
			LocalDate fechaActual = fechaHoraActual.toLocalDate();
			LocalTime horaActual = fechaHoraActual.toLocalTime();

			// Si es sábado o domingo, avanzar al lunes
			if (fechaActual.getDayOfWeek() == DayOfWeek.SATURDAY) {
				fechaActual = fechaActual.plusDays(2);
			} else if (fechaActual.getDayOfWeek() == DayOfWeek.SUNDAY) {
				fechaActual = fechaActual.plusDays(1);
			} else if (horaActual.isAfter(LocalTime.of(17, 59, 59)) && fechaActual.getDayOfWeek() == DayOfWeek.FRIDAY) {
				fechaActual = fechaActual.plusDays(3); // Salta directamente al lunes
			} else if (horaActual.isAfter(LocalTime.of(17, 59, 59))) {
				fechaActual = fechaActual.plusDays(1); // Caso normal
			}

			// Formatear la fecha como YYYYMMDD
			String fechaFormateada = fechaActual.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
			String prefijoBanca = "646RDAMB" + fechaFormateada;

			// Consulta SQL para obtener la última clave de rastreo
			String sql = "SELECT claverastreo FROM spei.orden WHERE claverastreo LIKE '646RDAM%' and fechaoperacion = ? ORDER BY id DESC LIMIT 1";
			List<String> resultados = jdbcTemplate.queryForList(sql, String.class, Integer.parseInt(fechaFormateada));

			String nuevaClave;
			if (!resultados.isEmpty()) {
				String ultimaClave = resultados.get(0);
				String numeroStr = ultimaClave.substring(ultimaClave.length() - 4);
				int numero = Integer.parseInt(numeroStr) + 1;

				String random = String.format("%04d", new Random().nextInt(9000) + 1000);
				nuevaClave = prefijoBanca + random + String.format("%04d", numero);
			} else {
				String random = String.format("%04d", new Random().nextInt(9000) + 1000);
				nuevaClave = prefijoBanca + random + "0001";
			}

			Map<String, Object> response = new HashMap<>();
			response.put("nuevaClave", nuevaClave);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

}
