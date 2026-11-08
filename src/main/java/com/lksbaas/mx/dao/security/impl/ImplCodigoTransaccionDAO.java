package com.lksbaas.mx.dao.security.impl;

import com.lksbaas.mx.dao.security.ICodigoTransaccionDAO;
import com.lksbaas.mx.model.security.CodigoTransaccion;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Implementación JDBC de persistencia para códigos de seguridad transaccionales.
 * Emplea bloqueos a nivel de fila (FOR UPDATE) en PostgreSQL para evitar condiciones de carrera.
 */
@Repository
public class ImplCodigoTransaccionDAO implements ICodigoTransaccionDAO {
    private static final String TXT_UPDATE_SEGURIDAD_CODIGO_TRAN = "UPDATE seguridad.codigo_transaccion ";
    private static final String TXT_SELECT_FROM_SEGURIDAD_CODIGO = "SELECT * FROM seguridad.codigo_transaccion ";


    private static final Logger log = LoggerFactory.getLogger(ImplCodigoTransaccionDAO.class);

    private final JdbcTemplate jdbcTemplate;

    public ImplCodigoTransaccionDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CodigoTransaccion> rowMapper = new CodigoTransaccionRowMapper();

    @Override
    public void guardar(CodigoTransaccion codigo) {
        String sql = "INSERT INTO seguridad.codigo_transaccion " +
                "(identificador_usuario, correo_destino, tipo_transaccion, codigo_hash, token_solicitud, " +
                "estado, intentos_fallidos, max_intentos, fecha_creacion, fecha_expiracion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                codigo.getIdentificadorUsuario(),
                codigo.getCorreoDestino(),
                codigo.getTipoTransaccion().name(),
                codigo.getCodigoHash(),
                codigo.getTokenSolicitud(),
                codigo.getEstado(),
                codigo.getIntentosFallidos(),
                codigo.getMaxIntentos(),
                Timestamp.valueOf(codigo.getFechaCreacion()),
                Timestamp.valueOf(codigo.getFechaExpiracion())
        );
    }

    @Override
    public Optional<CodigoTransaccion> obtenerPendienteParaActualizacion(String identificadorUsuario,
                                                                        TipoTransaccionSeguridad tipoTransaccion) {
        String sql = TXT_SELECT_FROM_SEGURIDAD_CODIGO +
                "WHERE identificador_usuario = ? AND tipo_transaccion = ? AND estado = 'PENDIENTE' " +
                "ORDER BY id DESC LIMIT 1 FOR UPDATE";
        try {
            CodigoTransaccion resultado = jdbcTemplate.queryForObject(sql, rowMapper,
                    identificadorUsuario, tipoTransaccion.name());
            return Optional.ofNullable(resultado);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CodigoTransaccion> obtenerPorTokenSolicitudParaActualizacion(String identificadorUsuario,
                                                                                TipoTransaccionSeguridad tipoTransaccion,
                                                                                String tokenSolicitud) {
        String sql = TXT_SELECT_FROM_SEGURIDAD_CODIGO +
                "WHERE identificador_usuario = ? AND tipo_transaccion = ? AND token_solicitud = ? AND estado = 'PENDIENTE' " +
                "FOR UPDATE";
        try {
            CodigoTransaccion resultado = jdbcTemplate.queryForObject(sql, rowMapper,
                    identificadorUsuario, tipoTransaccion.name(), tokenSolicitud);
            return Optional.ofNullable(resultado);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CodigoTransaccion> obtenerUltimoPorUsuarioYTipo(String identificadorUsuario,
                                                                   TipoTransaccionSeguridad tipoTransaccion) {
        String sql = TXT_SELECT_FROM_SEGURIDAD_CODIGO +
                "WHERE identificador_usuario = ? AND tipo_transaccion = ? " +
                "ORDER BY id DESC LIMIT 1";
        try {
            CodigoTransaccion resultado = jdbcTemplate.queryForObject(sql, rowMapper,
                    identificadorUsuario, tipoTransaccion.name());
            return Optional.ofNullable(resultado);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void invalidarPendientesAnteriores(String identificadorUsuario, TipoTransaccionSeguridad tipoTransaccion) {
        String sql = "UPDATE seguridad.codigo_transaccion SET estado = 'EXPIRADO' " +
                "WHERE identificador_usuario = ? AND tipo_transaccion = ? AND estado = 'PENDIENTE'";
        int actualizados = jdbcTemplate.update(sql, identificadorUsuario, tipoTransaccion.name());
        if (actualizados > 0) {
            log.debug("Se invalidaron {} códigos pendientes previos para el usuario {}", actualizados, identificadorUsuario);
        }
    }

    @Override
    public void registrarIntentoFallido(Long id, int nuevosIntentos, boolean bloquear) {
        String sql = TXT_UPDATE_SEGURIDAD_CODIGO_TRAN +
                "SET intentos_fallidos = ?, estado = CASE WHEN ? = true THEN 'BLOQUEADO' ELSE estado END " +
                "WHERE id = ?";
        jdbcTemplate.update(sql, nuevosIntentos, bloquear, id);
    }

    @Override
    public void marcarComoUsado(Long id, String tokenAutorizacion) {
        String sql = TXT_UPDATE_SEGURIDAD_CODIGO_TRAN +
                "SET estado = 'USADO', fecha_consumo = NOW(), token_autorizacion = ? " +
                "WHERE id = ?";
        jdbcTemplate.update(sql, tokenAutorizacion, id);
    }

    @Override
    public void marcarComoExpirado(Long id) {
        String sql = "UPDATE seguridad.codigo_transaccion SET estado = 'EXPIRADO' WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public boolean validarTokenAutorizacion(String identificadorUsuario,
                                            TipoTransaccionSeguridad tipoTransaccion,
                                            String tokenAutorizacion) {
        if (tokenAutorizacion == null || tokenAutorizacion.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM seguridad.codigo_transaccion " +
                "WHERE identificador_usuario = ? AND tipo_transaccion = ? AND token_autorizacion = ? " +
                "AND estado = 'USADO' AND fecha_consumo >= (NOW() - INTERVAL '10 minutes')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                identificadorUsuario, tipoTransaccion.name(), tokenAutorizacion.trim());
        return count != null && count > 0;
    }

    @Override
    public boolean consumirTokenAutorizacion(String identificadorUsuario,
                                             TipoTransaccionSeguridad tipoTransaccion,
                                             String tokenAutorizacion) {
        if (tokenAutorizacion == null || tokenAutorizacion.trim().isEmpty()) {
            return false;
        }
        String sql = TXT_UPDATE_SEGURIDAD_CODIGO_TRAN +
                "SET estado = 'CONSUMIDO_FINAL' " +
                "WHERE identificador_usuario = ? AND tipo_transaccion = ? AND token_autorizacion = ? AND estado = 'USADO'";
        int actualizados = jdbcTemplate.update(sql, identificadorUsuario, tipoTransaccion.name(), tokenAutorizacion.trim());
        return actualizados > 0;
    }

    private static class CodigoTransaccionRowMapper implements RowMapper<CodigoTransaccion> {
        @Override
        public CodigoTransaccion mapRow(ResultSet rs, int rowNum) throws SQLException {
            CodigoTransaccion codigo = new CodigoTransaccion();
            codigo.setId(rs.getLong("id"));
            codigo.setIdentificadorUsuario(rs.getString("identificador_usuario"));
            codigo.setCorreoDestino(rs.getString("correo_destino"));
            codigo.setTipoTransaccion(TipoTransaccionSeguridad.desdeCadena(rs.getString("tipo_transaccion")));
            codigo.setCodigoHash(rs.getString("codigo_hash"));
            codigo.setTokenSolicitud(rs.getString("token_solicitud"));
            codigo.setTokenAutorizacion(rs.getString("token_autorizacion"));
            codigo.setEstado(rs.getString("estado"));
            codigo.setIntentosFallidos(rs.getInt("intentos_fallidos"));
            codigo.setMaxIntentos(rs.getInt("max_intentos"));

            Timestamp creacion = rs.getTimestamp("fecha_creacion");
            if (creacion != null) {
                codigo.setFechaCreacion(creacion.toLocalDateTime());
            }
            Timestamp expiracion = rs.getTimestamp("fecha_expiracion");
            if (expiracion != null) {
                codigo.setFechaExpiracion(expiracion.toLocalDateTime());
            }
            Timestamp consumo = rs.getTimestamp("fecha_consumo");
            if (consumo != null) {
                codigo.setFechaConsumo(consumo.toLocalDateTime());
            }
            return codigo;
        }
    }
}

