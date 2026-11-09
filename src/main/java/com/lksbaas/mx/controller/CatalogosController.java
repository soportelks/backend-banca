package com.lksbaas.mx.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
	private final IDinamicoService serviceCatalogo;

	private final com.lksbaas.mx.service.impl.ImplUtilsServices utilsServices;

	public CatalogosController(IDinamicoService serviceCatalogo, com.lksbaas.mx.service.impl.ImplUtilsServices utilsServices) {
	    this.serviceCatalogo = serviceCatalogo;
	    this.utilsServices = utilsServices;
	}

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
			return ResponseEntity.ok(utilsServices.obtenerFechaOperacion());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping("/ultima-clave-rastreo")
	public ResponseEntity<Map<String, Object>> getUltimaClaveRastreo() {
		try {
			return ResponseEntity.ok(utilsServices.generarUltimaClaveRastreo());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

}
