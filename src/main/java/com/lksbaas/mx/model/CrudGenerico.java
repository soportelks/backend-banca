/**
 * 
 */
package com.lksbaas.mx.model;

import java.io.Serializable;

/**
 * @author lksbaassadecv
 *
 */
public class CrudGenerico implements Serializable{


	/**
	 * 
	 */
	private static final long serialVersionUID = 5614544223633484839L;
	/**
	 * INFOMACIÓN A PROCESAR
	 */
	private String[] datos;
	/**
	 * Accion a realizar
	 */
	private Integer accion;
	/**
	 * @return the datos
	 */
	public String[] getDatos() {
		return datos;
	}
	/**
	 * @param datos the datos to set
	 */
	public void setDatos(String[] datos) {
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
