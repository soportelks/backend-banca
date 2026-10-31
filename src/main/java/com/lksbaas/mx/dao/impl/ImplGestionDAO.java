package com.lksbaas.mx.dao.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lksbaas.mx.dao.IGestionDAO;
import com.lksbaas.mx.mapper.ResponseArray;
import com.lksbaas.mx.mapper.ResponseJsonMapper;

/**
 * @author Juan Eric Juárez Jaramillo
 * @descripcion : Clase para gestionar CRUDS y LISTAS desde la base de datos
 * @clase : ImplGestionDAO.java
 * @since 15 sep. 2022
 * @version : 1.0
 *
 */
@Repository
public class ImplGestionDAO implements IGestionDAO {

	/**
	 * Instancia de JDBCTemplate para el acceso a la BD
	 */
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Metodo para ejecutar querys de forma dinamica en
	 * Formato Generico JSON
	 * 
	 * @param query  - Query a consultar
	 * @param params - Parametros a filtrar
	 * @return {@link String} JSON generico
	 */
	@Override
	public String execQuery(String query, Object[] params) {
		return jdbcTemplate.queryForObject(query,
				new ResponseJsonMapper(), params);
	}

	/**
	 * Metodo que ejecutara una funcion generica tipo CRUD y retornara un Array
	 * 
	 * @param query  - Funcion a consumir
	 * @param params - Parametros de entrada de la funcion
	 * @return ${List<String[]>} - Retorna una lista de arreglo de respuesta
	 */
	@Override
	public String[] crud(String query, Object[] params) {
		String[] respuesta = null;
		try {
			respuesta = jdbcTemplate.queryForObject(query, new ResponseArray(), params);
		} catch (Exception e) {
			respuesta = new String[] { "0", "Ha ocurrido un error, por favor inténtelo de nuevo más tarde" };
		}
		return respuesta;
	}

}
