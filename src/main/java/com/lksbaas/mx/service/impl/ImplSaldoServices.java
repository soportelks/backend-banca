package com.lksbaas.mx.service.impl;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lksbaas.mx.model.ApiResponse;
import com.lksbaas.mx.model.Request;
import com.lksbaas.mx.service.IDinamicoService;

@Service
public class ImplSaldoServices {

    @Autowired
    private IDinamicoService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${url-saldos}")
    private String urlSaldos;

    @Value("${query.tesoreria.sps_saldomovimientobycliente_json}")
    private String querySaldoAhorroCliente;

    public boolean validarSaldo(ObjectNode json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode datosNode = (ObjectNode) json.get("datos");

            String datosJson = mapper.writeValueAsString(datosNode);
            String query = "SELECT * FROM tesoreria.sps_saldomovimientobycliente_json(?, ?)";
            Integer accion = json.get("accion").asInt();

            // Ejecutar consulta
            Map<String, Object> result = jdbcTemplate.queryForMap(query, datosJson, accion);

            // Extraer el JSON string del resultado
            String resultadoJson = (String) result.get("sps_saldomovimientobycliente_json");

            // Parsear ese JSON string
            JsonNode root = mapper.readTree(resultadoJson);
            String codigo = root.get("codigo").asText();

            if (!"0".equals(codigo)) {
                return false;
            }

            // Obtener saldo
            BigDecimal saldo = new BigDecimal(root.get("info").get("saldo").asText());
            BigDecimal montoConComicion = new BigDecimal(datosNode.get("montoConComicion").asText());

            // Validar que el saldo sea mayor o igual
            return saldo.compareTo(montoConComicion) >= 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public ApiResponse obtenerSaldo() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();

        String externalUrl = urlSaldos.trim();

        String requestJson = "{\n" +
                " \"cuentaOrdenante\": \"646180606500000000\",\n" +
                " \"empresa\": \"CAJA_RDAM\",\n" +
                " \"firma\": \"kLVJyWHGrQcVpoHC8FOBeg/MQQ2cDZhEgDl0Jut9ufwkZwIwhbbNmjS2cTEHKZINMstOjqsCyr3/N0frODbsmF8PjCWa1lZ/BRjD+iryNgR2j4wjrreeckNmGLIHgsc2iwbVtHLdAE1f/z/EWdAtoM4vkJAj4zPk044zDBnEDteJkWTHJ3TAoDknVWMLphwaL1J3CeNHzus4vObhboE6lHDp01AdPcHX/JyoWhTV6RtsVAHw7ZaY0B0G3VEeG+hU0cvvqKEaPfMLx8nF6cZJePVJJrQXs0sAtJ6Ffj07Fx3xLdzjMciZhyOyy6BprkOTb/iV9DnbcdUB2iQx3fp5MQ==\"\n"
                +
                "}";

        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                externalUrl, HttpMethod.POST, entity, ApiResponse.class);

        return response.getBody();
    }

    public Boolean validarSaldoAhorro(Request request) {
        String resultado = service.execQueryDinamico(request, querySaldoAhorroCliente);

        try {
            // Validar respuesta del SP
            if (resultado.contains("\"codigo\"") && !resultado.contains("\"codigo\" : \"0\"")) {
                return false;
            }

            // Extraer saldo
            double saldo = 0;
            int saldoIndex = resultado.indexOf("\"saldo\"");
            if (saldoIndex != -1) {
                int colonIndex = resultado.indexOf(":", saldoIndex);
                int comaIndex = resultado.indexOf("}", colonIndex);
                String saldoStr = resultado.substring(colonIndex + 1, comaIndex).trim();
                saldo = Double.parseDouble(saldoStr);
            }

            // Extraer monto desde request
            String datos = request.getDatos().toString();
            double monto = 0;
            if (datos.contains("monto")) {
                int montoIndex = datos.indexOf("monto");
                int colonIndex = datos.indexOf(":", montoIndex);
                int comaIndex = datos.indexOf(",", colonIndex);
                if (comaIndex == -1)
                    comaIndex = datos.length() - 1;
                String montoStr = datos.substring(colonIndex + 1, comaIndex).replaceAll("[^0-9.]", "").trim();
                monto = Double.parseDouble(montoStr);
            }

            if (monto <= 0 || monto > saldo) {
                return false;
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
