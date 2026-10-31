package com.lksbaas.mx.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.service.IDinamicoService;

@RestController
@CrossOrigin("*")
@RequestMapping("/digitalizacion")
public class DigitalizacionController {
	// Constante de JSON
	private static final String JSON_PROD_CONS = "application/json;charset=UTF-8";

	// Query para consulta de tipo documentos
	@Value("${query.onboarding.sps_tipo_documentos_json}")
	private String querySPSTipoDocumentos;

	// Query para CRUD tipo documentos
	@Value("${query.onboarding.crud_tipo_documentos}")
	private String queryCRUDCRUDTipoDocumentos;

	// Query para CRUD asigna documentos
	@Value("${query.onboarding.crud_asigna_documento}")
	private String queryCRUDCRUDAsignaDocumento;



	// Query para consulta de documentos
	@Value("${query.onboarding.sps_documentos_onboarding}")
	private String querySPSDocumentosOnboarding;

	// Query para consulta de documentos
	@Value("${query.onboarding.sps_imagenes_onboarding}")
	private String querySPSImagenesOnboarding;

	// Instancia service dinamico
	@Autowired
	private IDinamicoService service;

	@PostMapping(path = "/get-documentos-onboarding", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsDocumentosOnboarding(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSDocumentosOnboarding);
	}

	@PostMapping(path = "/get-imagenes-onboarding", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsImagenesOnboarding(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSImagenesOnboarding);
	}

	@PostMapping(path = "/get-tipo-documentos", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsTipoDocumentos(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSTipoDocumentos);
	}

	@PostMapping(path = "/crud-tipo-documentos", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String crudTipoDocumentos(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryCRUDCRUDTipoDocumentos);
	}

	@PostMapping(path = "/crud-asigna-documento", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String crudAsignaDocumento(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryCRUDCRUDAsignaDocumento);
	}

	
}