package com.lksbaas.mx.service;

import com.lksbaas.mx.model.CrudGenerico;

public interface ITransaccionesService {
	
	/**
	 * Que para el altay cancelacion de inversiones
	 * @param crud - Datos a registrar
	 * @return {@link String[]} - Arreglo con la respuesta de la peticion
	 */
	String[] crudInversion(CrudGenerico crud);

}
