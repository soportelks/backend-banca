/**
 * 
 */
package com.lksbaas.mx.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

/**
 * @author HP
 *
 */
public class ResponseArray  implements RowMapper<String[]>{

	@Override
	public String[] mapRow(ResultSet rs, int rowNum) throws SQLException {
		
		 var arr = rs.getArray(1);

		return (String[]) arr.getArray();
	}

}
