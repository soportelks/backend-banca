package com.lksbaas.mx.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import com.lksbaas.mx.dao.IGestionDAO;
import com.lksbaas.mx.model.CrudGenerico;
import com.lksbaas.mx.service.ITransaccionesService;

@Service
//@RefreshScope
public class ImplTransaccionesService implements ITransaccionesService{
	
	/**
	 * Query tip'o crud para el alta y cancelacion de inversiones
	 */
	@Value("${query.crudInversion}")
	private String queryCrudInversion;
	
	/**
	 * Instancia para acceso al DAO crud
	 */
	@Autowired
	private IGestionDAO crudTransaccion;

	@Override
	public String[] crudInversion(CrudGenerico crud) {
		return crudTransaccion.crud(queryCrudInversion, new Object[] {crud.getDatos(), crud.getAccion()});
	}

}
