package com.lksbaas.mx.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

public class ResponseJsonMapper implements RowMapper<String>{
	
	/**
	 * MEtodo Mapper para el seteo de la cadena 
	 * @return String - json a cadena
	 */
	@Override
	public String mapRow(ResultSet rs, int rowNum) throws SQLException {
			return rs.getString(1);
	}

}
