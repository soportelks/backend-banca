package com.lksbaas.mx.model;

import java.io.Serializable;

public class RequestParam implements Serializable {
	/**
	 * SERIAl ID
	 */
	private static final long serialVersionUID = -5519343838304528832L;

	/**
	 * Json para CRUD
	 */
	private Object datos;
	

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

}
