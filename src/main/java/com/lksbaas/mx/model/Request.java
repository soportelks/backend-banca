package com.lksbaas.mx.model;

import java.io.Serializable;

public class Request implements Serializable{
	
	/**
	 * SERIAl ID
	 */
	private static final long serialVersionUID = -5519343838304528832L;

	/**
	 * Json para CRUD
	 */
	private Object datos;
	
	/**
	 * Accion a realizar 1.- Inserta, 2. Actualiza
	 */
	private Integer accion;

	/**
	 * @return the datos
	 */
	public Object getDatos() {
		return datos;
	}

	/**
	 * @param datos the datos to set
	 */
	public void setDatos(Object datos) {
		this.datos = datos;
	}

	/**
	 * @return the accion
	 */
	public Integer getAccion() {
		return accion;
	}

	/**
	 * @param accion the accion to set
	 */
	public void setAccion(Integer accion) {
		this.accion = accion;
	}
	
	

}
