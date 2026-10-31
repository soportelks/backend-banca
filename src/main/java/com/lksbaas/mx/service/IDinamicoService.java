package com.lksbaas.mx.service;

import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.model.RequestParam;

public interface IDinamicoService {
	
	/**
	 * Firma que permite ejecutar consultas dinamicas
	 * @param request - Información a Registrar
	 * @param query - Query  a ejecutar
	 * @return {@link String} - Info de la Función
	 */
	String execQueryDinamico(Request request, String query);
	
	/**
	 * Firma que permite ejecutar consultas dinamicas
	 * @param request - Información a Registrar
	 * @param query - Query  a ejecutar
	 * @return {@link String} - Info de la Función
	 */
	String execQueryDinamico1Param(RequestParam request, String query);

}
