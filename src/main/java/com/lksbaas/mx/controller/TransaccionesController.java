package com.lksbaas.mx.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lksbaas.mx.model.CrudGenerico;
import com.lksbaas.mx.service.ITransaccionesService;

@RestController
@CrossOrigin("*")
@RequestMapping("/transaccion")
public class TransaccionesController {

	// Constante de JSON
	private static final String JSON_PROD_CONS = "application/json;charset=UTF-8";

	/**
	 * Servicio de acceso a datos
	 */
	@Autowired
	private ITransaccionesService service;

	/**
	 * Metodo para el alta y cancelacion de inversiones
	 * 
	 * @param crud - Datos a registrar
	 * @return {@link String[]} - Arreglo con la respuesta de la peticion
	 */
	@PostMapping(path = "/inversion", consumes = JSON_PROD_CONS, produces = JSON_PROD_CONS)
	public String[] crudInversion(@RequestBody CrudGenerico crud) {
		return service.crudInversion(crud);
	}

	/**
	 * Metodo para el crud de los prospectos
	 * 
	 * @PostMapping(path = "/venta-mtc", consumes = JSON_PROD_CONS, produces =
	 *                   JSON_PROD_CONS)
	 *                   public String crudClientes(@RequestBody Request request) {
	 *                   return serviceDinamico.execQueryDinamico(request,
	 *                   queryCRUDVentas);
	 *                   }
	 */

}
