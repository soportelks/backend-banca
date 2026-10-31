package com.lksbaas.mx.dao;

/**
 * @author Juan Eric Juárez Jaramillo
 * @descripcion : Que para gestionar CRUDS y LISTAS desde la base de datos
 * @clase : IGestionDAO.java
 * @since 15 sep. 2022
 * @version : 1.0
 *
 */
public interface IGestionDAO {
	
	
	
	/**
	 * Que para ejecutar querys de forma dinamica en Formato Generico JSON 
	 * @param query - Query a consultar
	 * @param params - Parametros a filtrar
	 * @return {@link String} JSON generico
	 */
	String execQuery(String query, Object[] params);
	
	/**
	 * Metodo que recibe un crud
	 * @param query - Crud a ejecutar
	 * @param params - Parametros a registrar
	 * @return {@link String[]} - REtorna un arreglo
	 */
	String[] crud(String query, Object[] params);

}
