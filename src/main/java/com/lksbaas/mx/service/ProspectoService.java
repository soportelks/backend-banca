package com.lksbaas.mx.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;

import com.lksbaas.mx.model.Prospecto;
import com.lksbaas.mx.util.AESUtil;

@Service
public class ProspectoService {
    private static final String KEY_CODIGO = "codigo";
    private static final String KEY_MENSAJE = "mensaje";
    private static final String TXT_PROSPECTO_NO_ENCONTRADO = "prospecto no encontrado";


    private final JdbcTemplate jdbcTemplate;

    @Value("${zonaMexico}")
    private String zonaMexico;

    private final AESUtil aesUtil;

    public ProspectoService(JdbcTemplate jdbcTemplate, AESUtil aesUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.aesUtil = aesUtil;
    }

    public Prospecto getProspecto(String numeroTelefono) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT " +
                            "pp.prospecto_id AS prospecto_id, " +
                            "pp.token_sesion AS token_sesion, " +
                            "pp.inicio_sesion AS inicio_sesion, " +
                            "pp.cve_prospecto AS clave_prospecto, " +
                            "(pp.nombre_prospecto || ' ' || pp.apellido_paterno_prospecto || ' ' || pp.apellido_materno_prospecto) AS nombre_prospecto, "
                            +
                            "pp.correo_electronico AS correo_electronico, " +
                            "pp.numero_telefono AS numero_telefono, " +
                            "pp.domicilio AS domicilio, " +
                            "cat.descripcion AS estatus_prospecto, " +
                            "pp.contrasena AS contrasena, " +
                            "cat.cve_generales AS cveGenerales, " +
                            "cli.numero_cliente AS numero_cliente, " +
                            "cl.clabe AS clabe, " +
                            "cl.estatus AS clabe_estatus, " +
                            "cl.tipo_debito AS tipo_debito, " +
                            "cli.cve_cliente AS clave_cliente, " +
                            "solOnb.solicitud_id AS solicitudId, " +
                            "solOnb.fecha_solicitud AS fechaSolicitud, " +
                            "catGen.descripcion AS descripcionSolicitud, " +
                            "solOnb.observaciones_solicitud AS observacionesSolicitud, " +
                            "catGen.generales_id AS generalesIdSolicitud, " +
                            "catGen.cve_generales AS cveGeneralesSolicitud, " +
                            "com.comision_spei AS comision_spei, " +
                            "com.saldo_minimo AS saldo_minimo " +
                            "FROM clientes.clientes_prospectos cp " +
                            "INNER JOIN clientes.clientes cli ON cp.cliente_id = cli.cliente_id " +
                            "INNER JOIN onboarding.prospecto pp ON cp.prospecto_id = pp.prospecto_id " +
                            "INNER JOIN catalogos.catalogo_generales cat ON pp.estatus_prospecto_id = cat.generales_id "
                            +
                            "LEFT JOIN clientes.clabes_clientes cl ON cli.cliente_id = cl.cliente_id " +
                            "LEFT JOIN onboarding.solicitud_onboarding solOnb ON pp.prospecto_id = solOnb.prospecto_id "
                            +
                            "LEFT JOIN catalogos.catalogo_generales catGen ON solOnb.estatus_solicitud_id = catGen.generales_id "
                            +
                            "LEFT JOIN spei.comisiones com ON cli.cliente_id = com.idcliente " +
                            "WHERE pp.numero_telefono = ? and cp.tipo_sistema_id = 2",
                    new BeanPropertyRowMapper<>(Prospecto.class),
                    numeroTelefono);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean validarCredenciales(String numeroTelefono, String contrasenaPlano) {
        Prospecto prospecto = getProspecto(numeroTelefono);
        if (prospecto == null) {
            return false;
        }

        try {
            String contrasenaDescifrada = aesUtil.descifrar(prospecto.getContrasena());
            return contrasenaDescifrada.equals(contrasenaPlano);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sesionActiva(String numeroTelefono) {
        Boolean sesion = jdbcTemplate.queryForObject(
                "SELECT sesion FROM onboarding.prospecto WHERE numero_telefono = ?",
                Boolean.class,
                numeroTelefono);
        return Boolean.TRUE.equals(sesion);
    }

    public void guardarToken(String numeroTelefono, String token) {
        jdbcTemplate.update(
                "UPDATE onboarding.prospecto SET token_sesion = ? WHERE numero_telefono = ?",
                token, numeroTelefono);
    }

    public boolean cerrarSesion(String token) {
        try {
            String sql = "UPDATE onboarding.prospecto SET token_sesion = NULL WHERE token_sesion = ?";
            int updated = jdbcTemplate.update(sql, token);
            return updated > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verificarContrasena(Integer prospectoId, String contrasenaIngresada) {
        try {
            // Obtener la contraseña cifrada desde la base de datos
            String contrasenaCifradaDB = jdbcTemplate.queryForObject(
                    "SELECT contrasena FROM onboarding.prospecto WHERE prospecto_id = ?",
                    String.class,
                    prospectoId);

            // Descifrar ambas contraseñas
            String contrasenaDescifradaDB = aesUtil.descifrar(contrasenaCifradaDB);

            // Comparar y retornar resultado
            return contrasenaDescifradaDB.equals(contrasenaIngresada);
        } catch (EmptyResultDataAccessException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Map<String, Object> cambiarContrasena(Integer prospectoId, String contrasenaNueva, String contrasenaActual) {
        Map<String, Object> respuesta = new HashMap<>();
        try {
            boolean valida = verificarContrasena(prospectoId, contrasenaActual);
            if (valida) {
                String contrasenaCifrada = aesUtil.cifrar(contrasenaNueva);
                jdbcTemplate.update(
                        "UPDATE onboarding.prospecto SET contrasena = ? WHERE prospecto_id = ?",
                        contrasenaCifrada, prospectoId);
                respuesta.put(KEY_CODIGO, 0);
                respuesta.put(KEY_MENSAJE, "contraseña actualizada correctamente");
            } else {
                respuesta.put(KEY_CODIGO, 1);
                respuesta.put(KEY_MENSAJE, "la contraseña no coincide con la actual");
            }
        } catch (EmptyResultDataAccessException e) {
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, TXT_PROSPECTO_NO_ENCONTRADO);
        } catch (Exception e) {
            e.printStackTrace();
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, "error al actualizar la contraseña");
        }
        return respuesta;
    }

    public Map<String, Object> cambiarContrasenaRestauracion(Integer prospectoId, String contrasenaNueva) {
        Map<String, Object> respuesta = new HashMap<>();
        try {

            String contrasenaCifrada = aesUtil.cifrar(contrasenaNueva);
            jdbcTemplate.update(
                    "UPDATE onboarding.prospecto SET contrasena = ? WHERE prospecto_id = ?",
                    contrasenaCifrada, prospectoId);
            respuesta.put(KEY_CODIGO, 0);
            respuesta.put(KEY_MENSAJE, "contraseña actualizada correctamente");

        } catch (EmptyResultDataAccessException e) {
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, TXT_PROSPECTO_NO_ENCONTRADO);
        } catch (Exception e) {
            e.printStackTrace();
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, "error al actualizar la contraseña");
        }
        return respuesta;
    }

    public Map<String, Object> encriptarContrasena(String contrasena) {
        Map<String, Object> respuesta = new HashMap<>();
        try {
            String contrasenaCifrada = aesUtil.cifrar(contrasena);
            respuesta.put(KEY_CODIGO, 0);
            respuesta.put(KEY_MENSAJE, "Contraseña cifrada correctamente");
            respuesta.put("contraseña", contrasenaCifrada);
        } catch (Exception e) {
            e.printStackTrace();
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, "Error al cifrar la contraseña");
        }
        return respuesta;
    }

    public Map<String, Object> desencriptarContrasena(String contrasena) {
        Map<String, Object> respuesta = new HashMap<>();
        try {
            String contrasenaDescifrada = aesUtil.descifrar(contrasena);
            respuesta.put(KEY_CODIGO, 0);
            respuesta.put(KEY_MENSAJE, "Contraseña actualizada correctamente");
            respuesta.put("contraseña", contrasenaDescifrada);
        } catch (Exception e) {
            e.printStackTrace();
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, "Error al desencriptar la contraseña");
        }
        return respuesta;
    }

    public String obtenerToken(String numeroTelefono) {
        try {
            List<String> tokens = jdbcTemplate.queryForList(
                    "SELECT token_sesion FROM onboarding.prospecto WHERE numero_telefono = ? ORDER BY prospecto_id DESC LIMIT 1",
                    String.class, numeroTelefono);
            return tokens.isEmpty() ? null : tokens.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public void eliminarToken(String numeroTelefono) {
        jdbcTemplate.update(
                "UPDATE onboarding.prospecto SET token_sesion = NULL WHERE numero_telefono = ?",
                numeroTelefono);
    }

    public Map<String, Object> validadEstado(String cveGeneralesSolicitud) {
        Map<String, Object> respuesta = new HashMap<>();

        try {
            switch (cveGeneralesSolicitud) {
                case "88SR": // 88SR
                    respuesta.put(KEY_CODIGO, 1);
                    respuesta.put(KEY_MENSAJE, "redirigir a /validacion");
                    break;
                case "88PR": // 88PR
                    respuesta.put(KEY_CODIGO, 1);
                    respuesta.put(KEY_MENSAJE, "Parece que no has subido tus documentos. Continúa con tu proceso.");
                    break;
                case "88IN": // 88IN
                    respuesta.put(KEY_CODIGO, 1);
                    respuesta.put(KEY_MENSAJE, "Tu cuenta está desactivada por el momento");
                    break;
                default:
                    respuesta.put(KEY_CODIGO, 0);
                    respuesta.put(KEY_MENSAJE, "Correcto");
            }
        } catch (EmptyResultDataAccessException e) {
            respuesta.put(KEY_CODIGO, 1);
            respuesta.put(KEY_MENSAJE, TXT_PROSPECTO_NO_ENCONTRADO);
        }

        return respuesta;
    }

    public void actualizarInicioSesion(String numeroTelefono) {
        String sql = "UPDATE onboarding.prospecto SET inicio_sesion = (now() AT TIME ZONE ?) WHERE numero_telefono = ?";
        jdbcTemplate.update(sql, zonaMexico, numeroTelefono);
    }

}