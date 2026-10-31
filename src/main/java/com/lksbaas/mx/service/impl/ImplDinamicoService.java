package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lksbaas.mx.dao.IGestionDAO;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.model.RequestParam;
import com.lksbaas.mx.service.IDinamicoService;

@Service
public class ImplDinamicoService implements IDinamicoService {

	@Autowired
	private IGestionDAO dao;

	/**
	 * Firma que permite ejecutar consultas dinamicas
	 * 
	 * @param request - Información a Registrar
	 * @param query   - Query a ejecutar
	 * @return {@link String} - Info de la Función
	 */
	@Override
	public String execQueryDinamico(Request request,
			String query) {
		var resultado = "";
		try {
			var json = new ObjectMapper().writeValueAsString(request.getDatos());

			resultado = dao.execQuery(query,
					new Object[] { json, request.getAccion() });

		} catch (JsonProcessingException e) {
			resultado = "{\"codigo\":5,\"mensaje\":\"" + e.getMessage() + "\"}";
		}

		return resultado;
	}

	@Override
	public String execQueryDinamico1Param(RequestParam request, String query) {
		var resultado = "";
		try {
			var json = new ObjectMapper().writeValueAsString(request.getDatos());

			resultado = dao.execQuery(query,
					new Object[] { json });

		} catch (JsonProcessingException e) {
			resultado = "{\"codigo\":5,\"mensaje\":\"" + e.getMessage() + "\"}";
		}

		return resultado;
	}

}
