package com.lksbaas.mx.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;
import com.lksbaas.mx.service.impl.ComprobantesService;
import com.lksbaas.mx.service.impl.ImplPagosMasivosServices;
import com.lksbaas.mx.service.impl.ImplPagosServices;
import com.lksbaas.mx.service.impl.ImplSaldoServices;
import com.lksbaas.mx.service.impl.ImplTraspasosServices;
import com.lksbaas.mx.service.impl.implComicionesServices;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequestMapping("/tesoreria")
public class TesoreriaController {

	@Autowired
	private ImplPagosServices pagosServices;

	@Autowired
	private ImplPagosMasivosServices pagosServicesMasivos;

	@Autowired
	private ComprobantesService comprobantesService;

	@Autowired
	private ImplTraspasosServices traspasos;

	@Autowired
	private ImplSaldoServices saldo;

	@Autowired
	private implComicionesServices comiciones;

	@Value("${urlBanca}")
	private String urlBanca;

	@Value("${url-comprobante}")
	private String urlComprobante;

	// Constante de JSON
	private static final String JSON_PROD_CONS = "application/json;charset=UTF-8";
	//
	// Query para mostrar movimientos de cliente
	@Value("${query.tesoreria.sps_mov_historico_cliente_json}")
	private String queryMovHistoricoCliente;

	// Query para mostrar saldo de cliente cuenta ahorro
	@Value("${query.tesoreria.sps_saldomovimientobycliente_json}")
	private String querySaldoAhorroCliente;

	// Query obtener las inversiones de un cliente
	@Value("${query.tesoreria.sps_inversiones_cliente_onboarding}")
	private String queryInversionesCliente;

	// Inversiones
	// Query para obtener plazos de inversion
	@Value("${query.tesoreria.sps_montos_inversion}")
	private String queryMontosInversion;

	// Query para calcular inversiones
	@Value("${query.tesoreria.sps_calcular_inversiones}")
	private String queryCalcularInversiones;

	@Value("${query.tesoreria.sps_creditos_cliente}")
	private String querySPSCreditosClientes;

	@Value("${query.tesoreria.sps_calcula_pago}")
	private String querySPSCalculaPago;

	// Query para calcular inversiones
	@Value("${query.catalogos.sps_recargas}")
	private String querySpsRecargasClientes;

	@Value("${query.onboarding.sps_inmovimientos_json}")
	private String querySPSMovivientoComprobante;

	@Value("${query.onboarding.sps_cliente_clabe_json}")
	private String querySPSClienteTraspaso;

	@Value("${query.tesoreria.sps_edo_cuenta}")
	private String queryEstadoCuenta;

	@Autowired
	private IDinamicoService service;

	@PostMapping(path = "/get-mov-historico-cliente", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsMovHistoricoCliente(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryMovHistoricoCliente);
	}

	@PostMapping(path = "/get-saldo-ahorro", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsSaldoAhorroCliente(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySaldoAhorroCliente);
	}

	@PostMapping(path = "/get-inversiones-cliente", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsInversionesCliente(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryInversionesCliente);
	}

	@PostMapping(path = "/get-montos-inversion", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsMontosInversion(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryMontosInversion);
	}

	@PostMapping(path = "/calcular-inversiones", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String calcularInversiones(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryCalcularInversiones);
	}

	@PostMapping(path = "/get-creditos-clientes", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsCredCliente(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSCreditosClientes);
	}

	@PostMapping(path = "/get-pagos", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsPagosCliente(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySPSCalculaPago);
	}

	@PostMapping(path = "/get-recargas-montos", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsRecargasClientes(@RequestBody Request request) {
		return service.execQueryDinamico(request, querySpsRecargasClientes);
	}

	@PostMapping(path = "/get-info-movimiento", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsMovimientoComprobante(@RequestBody RequestParam request) {
		return service.execQueryDinamico1Param(request, querySPSMovivientoComprobante);
	}

	@PostMapping(path = "/get-clave-cliente", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String spsclienteTraspaso(@RequestBody RequestParam request) {
		return service.execQueryDinamico1Param(request, querySPSClienteTraspaso);
	}

	@PostMapping(path = "/genera-estado-cuenta", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String generarEstadoCuenta(@RequestBody Request request) {
		return service.execQueryDinamico(request, queryEstadoCuenta);
	}

	@PostMapping("/registrar-spei")
	public ResponseEntity<String> registrarPagoSpei(@RequestBody ObjectNode json) {
		try {
			String responseJson = pagosServices.registrarSPEI(json);
			return ResponseEntity.ok(responseJson);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"mensaje\": \"Error al validar la orden: " + e.getMessage() + "\", \"status\": 500}");
		}
	}

	@PostMapping("/registrar-spei-masivos")
	public ResponseEntity<String> registrarPagoSpeiMasivos(@RequestBody ObjectNode json) {
		try {
			String responseJson = pagosServicesMasivos.registrarSPEI(json);
			return ResponseEntity.ok(responseJson);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"mensaje\": \"Error al validar la orden: " + e.getMessage() + "\", \"status\": 500}");
		}
	}

	@PostMapping("/comprobante-pagos")
	public ResponseEntity<String> comprobantePago(@RequestBody ObjectNode json) {
		try {
			String respuesta = comprobantesService.comprobantePagoSTP(json);
			return ResponseEntity.ok(respuesta);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"mensaje\": \"Error al obtener el comprobante: " + e.getMessage()
							+ "\", \"status\": 500}");
		}
	}

	@PostMapping("/enviar-email")
	public ResponseEntity<Map<String, String>> comprobantePagoEmail(@RequestBody ObjectNode json) {
		try {
			Map<String, String> respuesta = comprobantesService.enviarComprobantePorEmail(json);
			return ResponseEntity.ok(respuesta);
		} catch (Exception e) {
			Map<String, String> errorResponse = Map.of(
					"mensaje", "Error al obtener el comprobante: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@PostMapping("/enviar-movimientos-traspaso")
	public ResponseEntity<Map<String, Object>> registMovimientoTraspaso(@RequestBody Map<String, Object> request) {
		try {
			Map<String, Object> resultado = traspasos.procesarMovimientoTraspaso(request);
			if (resultado.containsKey("error")) {
				return ResponseEntity.badRequest().body(resultado);
			}
			return ResponseEntity.ok(resultado);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(
					Map.of("error", "Error interno: " + e.getMessage()));
		}
	}

	@PostMapping(path = "/validar-saldo-ahorro", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public Boolean validarSaldoAhorro(@RequestBody Request request) {
		return saldo.validarSaldoAhorro(request);
	}

	@PostMapping("/comiciones-cliente")
	public ResponseEntity<Map<String, Object>> obtenerComisiones(@RequestBody Map<String, String> request) {
		String numeroCliente = request.get("numero_cliente");

		if (numeroCliente == null || numeroCliente.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(
					Map.of("codigo", 1, "mensaje", "El campo 'numero_cliente' es obligatorio"));
		}

		Map<String, Object> resultado = comiciones.obtenerComisionesPorNumeroCliente(numeroCliente);

		int codigo = (int) resultado.get("codigo");
		if (codigo != 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resultado);
		}

		return ResponseEntity.ok(resultado);
	}

}
