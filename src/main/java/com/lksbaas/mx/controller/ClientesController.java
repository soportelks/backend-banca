package com.lksbaas.mx.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;
import com.lksbaas.mx.service.ProspectoService;

@RestController
@RequestMapping("/onboarding")
public class ClientesController {
	private static final String KEY_CONTRASENA = "contrasena";

	// Constante de JSON
	private static final String JSON_PROD_CONS = "application/json;charset=UTF-8";

	private final ProspectoService prospecto;

	// Clientes
	// Query para consulta de clientes
	@Value("${query.onboarding.sps_prospectos}")
	private String querySPSClientes;

	// Query CRUD clientes
	@Value("${query.onboarding.crud_prospectos}")
	private String queryCRUDClientes;

	// Solicitud Onboarding
	// Query para consulta de solicitudes
	@Value("${query.onboarding.sps_solicitud_onboarding}")
	private String querySPSSolicitudes;

	// Query revision de solicitudes
	@Value("${query.onboarding.revisar_solicitud_onboarding}")
	private String queryRevisionSolicitudes;

	// Query para CRUD documento codificado
	@Value("${query.onboarding.crud_documento_json}")
	private String queryCRUDCRUDDocumentoJSON;

	// Query Login Cliente
	@Value("${query.onboarding.sps_login_clientes}")
	private String queryLoginClientes;

	// Query update contacto
	@Value("${query.onboarding.update_prospecto_contacto}")
	private String queryUpdateProspecto;

	// Inversiones
	// Query para obtener plazos de inversion
	@Value("${query.tesoreria.sps_montos_inversion}")
	private String queryMontosInversion;

	// Query para calcular inversiones
	@Value("${query.tesoreria.sps_calcular_inversiones}")
	private String queryCalcularInversiones;

	// Query para calcular inversiones
	@Value("${query.onboarding.toggle_sesion_prospecto}")
	private String queryCheckSesion;

	// Instancia service dinamico
	private final IDinamicoService service;

	public ClientesController(IDinamicoService service, ProspectoService prospecto) {
	    this.service = service;
	    this.prospecto = prospecto;
	}

	@PostMapping(path = "/get-prospectos", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsClientes(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSClientes);
	}

	@PostMapping(path = "/get-solicitudes-onboarding", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsSolicitudes(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSSolicitudes);
	}

	@PostMapping(path = "/crud-prospectos", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String crudClientes(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryCRUDClientes);
	}

	@PostMapping(path = "/revision-solicitudes-onboarding", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String revisionSolicitudes(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryRevisionSolicitudes);
	}

	@PostMapping(path = "/crud-documento-json", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String queryCRUDCRUDDocumentoJSON(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryCRUDCRUDDocumentoJSON);
	}

	@PostMapping(path = "/login-clientes", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String queryLoginClientes(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryLoginClientes);
	}

	@PostMapping(path = "/update-contacto", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public Map<String, Object> queryUpdateProspectoContacto(@RequestBody Map<String, Object> request) {
		Integer prospectoId = (Integer) request.get("prospecto_id");
		String contrasenaNueva = (String) request.get(KEY_CONTRASENA);
		String contrasenaActual = (String) request.get("contrasenaActual");

		return prospecto.cambiarContrasena(prospectoId, contrasenaNueva, contrasenaActual);
	}

	@PostMapping(path = "/restaurar-contacto", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public Map<String, Object> queryUpdateContrasena(@RequestBody Map<String, Object> request) {
		Integer prospectoId = (Integer) request.get("prospecto_id");
		String contrasenaNueva = (String) request.get(KEY_CONTRASENA);

		return prospecto.cambiarContrasenaRestauracion(prospectoId, contrasenaNueva);
	}

	@PostMapping(path = "/get-montos-inversion", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsMontosInversion(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryMontosInversion);
	}

	@PostMapping(path = "/calcular-inversiones", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String calcularInversiones(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryCalcularInversiones);
	}

	@PostMapping(path = "/check-sesion", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String cambiarSesion(@RequestBody RequestParam request) {
		return service.execQueryDinamico1Param(request, queryCheckSesion);
	}

	@PostMapping(path = "/cifrar-contrasena", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public Map<String, Object> cifrarContrasena(@RequestBody Map<String, Object> request) {
		String contrasena = (String) request.get(KEY_CONTRASENA);
		return prospecto.encriptarContrasena(contrasena);
	}

	@PostMapping(path = "/descifrar-contrasena", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public Map<String, Object> descifrarContrasena(@RequestBody Map<String, Object> request) {
		String contrasena = (String) request.get(KEY_CONTRASENA);
		return prospecto.desencriptarContrasena(contrasena);
	}

}