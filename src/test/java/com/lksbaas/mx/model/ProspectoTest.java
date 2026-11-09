package com.lksbaas.mx.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProspectoTest {

    @Test
    @DisplayName("Prospecto getters y setters camelCase")
    void testGettersYSetters() {
        Prospecto p = new Prospecto();

        p.setProspectoId(1);
        assertEquals(1, p.getProspectoId());

        p.setClaveProspecto("CP1");
        assertEquals("CP1", p.getClaveProspecto());

        p.setNombreProspecto("Juan");
        assertEquals("Juan", p.getNombreProspecto());

        p.setCorreoElectronico("juan@test.com");
        assertEquals("juan@test.com", p.getCorreoElectronico());

        p.setNumeroTelefono("5512345678");
        assertEquals("5512345678", p.getNumeroTelefono());

        p.setDomicilio("Calle 1");
        assertEquals("Calle 1", p.getDomicilio());

        p.setEstatusProspecto("ACTIVO");
        assertEquals("ACTIVO", p.getEstatusProspecto());

        p.setContrasena("pass123");
        assertEquals("pass123", p.getContrasena());

        p.setCveGenerales("GEN1");
        assertEquals("GEN1", p.getCveGenerales());

        p.setNumeroCliente("NC1");
        assertEquals("NC1", p.getNumeroCliente());

        p.setClabe("646180606500000000");
        assertEquals("646180606500000000", p.getClabe());

        p.setClabeEstatus("ACTIVA");
        assertEquals("ACTIVA", p.getClabeEstatus());

        p.setTipoDebito("DEBITO");
        assertEquals("DEBITO", p.getTipoDebito());

        p.setClaveCliente("CLI1");
        assertEquals("CLI1", p.getClaveCliente());

        p.setSolicitudId(10);
        assertEquals(10, p.getSolicitudId());

        p.setFechaSolicitud("2026-11-08");
        assertEquals("2026-11-08", p.getFechaSolicitud());

        p.setDescripcionSolicitud("Apertura");
        assertEquals("Apertura", p.getDescripcionSolicitud());

        p.setObservacionesSolicitud("Ninguna");
        assertEquals("Ninguna", p.getObservacionesSolicitud());

        p.setGeneralesIdSolicitud(20);
        assertEquals(20, p.getGeneralesIdSolicitud());

        p.setCveGeneralesSolicitud("GEN_SOL");
        assertEquals("GEN_SOL", p.getCveGeneralesSolicitud());

        p.setTokenSesion("tok123");
        assertEquals("tok123", p.getTokenSesion());

        p.setInicioSesion("2026-11-08 12:00:00");
        assertEquals("2026-11-08 12:00:00", p.getInicioSesion());

        p.setComisionSpei(new BigDecimal("5.00"));
        assertEquals(new BigDecimal("5.00"), p.getComisionSpei());

        p.setSaldoMinimo(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("100.00"), p.getSaldoMinimo());
    }
}

