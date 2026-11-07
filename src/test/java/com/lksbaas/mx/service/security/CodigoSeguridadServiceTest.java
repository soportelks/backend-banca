package com.lksbaas.mx.service.security;

import com.lksbaas.mx.config.SeguridadCodigoProperties;
import com.lksbaas.mx.dao.security.ICodigoTransaccionDAO;
import com.lksbaas.mx.model.Prospecto;
import com.lksbaas.mx.model.security.CodigoTransaccion;
import com.lksbaas.mx.model.security.RespuestaCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.RespuestaValidarCodigoDTO;
import com.lksbaas.mx.model.security.SolicitudCodigoSeguridadDTO;
import com.lksbaas.mx.model.security.SolicitudValidarCodigoDTO;
import com.lksbaas.mx.model.security.TipoTransaccionSeguridad;
import com.lksbaas.mx.service.ProspectoService;
import com.lksbaas.mx.service.security.impl.ImplCodigoSeguridadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodigoSeguridadServiceTest {

    @Mock
    private ICodigoTransaccionDAO dao;

    @Mock
    private INotificadorCodigoSeguridad notificador;

    @Mock
    private SeguridadCodigoProperties properties;

    @Mock
    private ProspectoService prospectoService;

    @InjectMocks
    private ImplCodigoSeguridadService service;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        lenient().when(properties.getExpiracionMinutos()).thenReturn(2);
        lenient().when(properties.getMaxIntentos()).thenReturn(3);
        lenient().when(properties.getLongitud()).thenReturn(6);
        lenient().when(properties.getCooldownSegundos()).thenReturn(60);
    }

    @Test
    @DisplayName("Generación y asociación correcta: emite código numérico seguro y despacha notificación sin exponer código en respuesta")
    void testGenerarCodigo_Exito() {
        String usuario = "5512345678";
        SolicitudCodigoSeguridadDTO solicitud = new SolicitudCodigoSeguridadDTO("TRANSFERENCIA_SPEI");

        Prospecto prospecto = new Prospecto();
        prospecto.setCorreo_electronico("cliente@banca.com");
        when(prospectoService.getProspecto(usuario)).thenReturn(prospecto);

        when(properties.resolverCorreoDestino(eq("cliente@banca.com"), any())).thenReturn("cliente@banca.com");
        when(properties.isUsarCorreoPrueba()).thenReturn(false);
        when(dao.obtenerUltimoPorUsuarioYTipo(eq(usuario), eq(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI)))
                .thenReturn(Optional.empty());

        RespuestaCodigoSeguridadDTO respuesta = service.solicitarCodigo(usuario, solicitud);

        assertNotNull(respuesta);
        assertEquals(0, respuesta.getCodigo());
        assertEquals("TRANSFERENCIA_SPEI", respuesta.getTipoTransaccion());
        assertNotNull(respuesta.getTokenSolicitud());
        assertTrue(respuesta.getExpiracionSegundos() > 0);

        // Verificar que el DAO guardó el hash y no el código plano
        ArgumentCaptor<CodigoTransaccion> captor = ArgumentCaptor.forClass(CodigoTransaccion.class);
        verify(dao, times(1)).guardar(captor.capture());
        CodigoTransaccion guardado = captor.getValue();
        assertEquals(usuario, guardado.getIdentificadorUsuario());
        assertEquals(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, guardado.getTipoTransaccion());
        assertEquals("PENDIENTE", guardado.getEstado());
        assertTrue(guardado.getCodigoHash().startsWith("$2a$") || guardado.getCodigoHash().startsWith("$2b$"));

        // Verificar que se invocó el notificador con el correo resuelto
        verify(notificador, times(1)).enviarCodigo(
                eq("cliente@banca.com"),
                anyString(),
                eq(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI),
                eq(2),
                eq(false)
        );
    }

    @Test
    @DisplayName("Configuración temporal de pruebas: redirige al correo de prueba configurado")
    void testGenerarCodigo_UsaCorreoPrueba() {
        String usuario = "5512345678";
        SolicitudCodigoSeguridadDTO solicitud = new SolicitudCodigoSeguridadDTO("TRANSFERENCIA_SPEI");

        when(properties.resolverCorreoDestino(any(), any())).thenReturn("contacto@multiplicandooportunidades.com");
        when(properties.isUsarCorreoPrueba()).thenReturn(true);
        when(dao.obtenerUltimoPorUsuarioYTipo(any(), any())).thenReturn(Optional.empty());

        RespuestaCodigoSeguridadDTO respuesta = service.solicitarCodigo(usuario, solicitud);

        assertEquals(0, respuesta.getCodigo());
        verify(notificador, times(1)).enviarCodigo(
                eq("contacto@multiplicandooportunidades.com"),
                anyString(),
                eq(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI),
                eq(2),
                eq(true)
        );
    }

    @Test
    @DisplayName("Protección contra spam: cooldown activo rechaza solicitud repetida")
    void testGenerarCodigo_CooldownActivo() {
        String usuario = "5512345678";
        SolicitudCodigoSeguridadDTO solicitud = new SolicitudCodigoSeguridadDTO("TRANSFERENCIA_SPEI");

        CodigoTransaccion previo = new CodigoTransaccion();
        previo.setFechaCreacion(LocalDateTime.now().minusSeconds(10)); // hace sólo 10 segundos
        when(dao.obtenerUltimoPorUsuarioYTipo(eq(usuario), eq(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI)))
                .thenReturn(Optional.of(previo));

        RespuestaCodigoSeguridadDTO respuesta = service.solicitarCodigo(usuario, solicitud);

        assertEquals(429, respuesta.getCodigo());
        assertTrue(respuesta.getMensaje().contains("espera"));
        verify(dao, never()).guardar(any());
        verify(notificador, never()).enviarCodigo(any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("Validación correcta: emite token de autorización y marca código como USADO")
    void testValidarCodigo_Correcto() {
        String usuario = "5512345678";
        String codigoPlano = "654321";
        String hash = passwordEncoder.encode(codigoPlano);

        CodigoTransaccion entity = new CodigoTransaccion(
                usuario, "cliente@banca.com", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                hash, "sol-uuid-1", 3, LocalDateTime.now(), LocalDateTime.now().plusMinutes(2)
        );
        entity.setId(101L);

        when(dao.obtenerPendienteParaActualizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI))
                .thenReturn(Optional.of(entity));

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", codigoPlano);
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuario, solicitud);

        assertTrue(respuesta.isValido());
        assertEquals(0, respuesta.getCodigo());
        assertNotNull(respuesta.getTokenAutorizacion());
        verify(dao, times(1)).marcarComoUsado(eq(101L), anyString());
    }

    @Test
    @DisplayName("Validación incorrecta: incrementa contador de intentos fallidos")
    void testValidarCodigo_Incorrecto_IncrementaIntentos() {
        String usuario = "5512345678";
        String hash = passwordEncoder.encode("111111");

        CodigoTransaccion entity = new CodigoTransaccion(
                usuario, "cliente@banca.com", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                hash, "sol-uuid-1", 3, LocalDateTime.now(), LocalDateTime.now().plusMinutes(2)
        );
        entity.setId(102L);
        entity.setIntentosFallidos(0);

        when(dao.obtenerPendienteParaActualizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI))
                .thenReturn(Optional.of(entity));

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", "999999");
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuario, solicitud);

        assertFalse(respuesta.isValido());
        assertEquals(1, respuesta.getCodigo());
        assertEquals(2, respuesta.getIntentosRestantes()); // 3 max - 1 intento
        verify(dao, times(1)).registrarIntentoFallido(102L, 1, false);
        verify(dao, never()).marcarComoUsado(anyLong(), anyString());
    }

    @Test
    @DisplayName("Protección contra fuerza bruta: supera límite de intentos y bloquea el código")
    void testValidarCodigo_SuperaMaxIntentos_Bloquea() {
        String usuario = "5512345678";
        String hash = passwordEncoder.encode("111111");

        CodigoTransaccion entity = new CodigoTransaccion(
                usuario, "cliente@banca.com", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                hash, "sol-uuid-1", 3, LocalDateTime.now(), LocalDateTime.now().plusMinutes(2)
        );
        entity.setId(103L);
        entity.setIntentosFallidos(2); // Ya tenía 2 intentos fallidos

        when(dao.obtenerPendienteParaActualizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI))
                .thenReturn(Optional.of(entity));

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", "888888");
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuario, solicitud);

        assertFalse(respuesta.isValido());
        assertEquals(2, respuesta.getCodigo()); // Código 2 = Bloqueado
        assertEquals(0, respuesta.getIntentosRestantes());
        assertTrue(respuesta.getMensaje().toLowerCase().contains("bloqueado"));
        verify(dao, times(1)).registrarIntentoFallido(103L, 3, true);
    }

    @Test
    @DisplayName("Expiración de códigos: rechaza código cuando supera tiempo de expiración y actualiza estado")
    void testValidarCodigo_Expirado() {
        String usuario = "5512345678";
        String hash = passwordEncoder.encode("123456");

        CodigoTransaccion entity = new CodigoTransaccion(
                usuario, "cliente@banca.com", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                hash, "sol-uuid-1", 3,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(3) // Expiró hace 3 minutos
        );
        entity.setId(104L);

        when(dao.obtenerPendienteParaActualizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI))
                .thenReturn(Optional.of(entity));

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", "123456");
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuario, solicitud);

        assertFalse(respuesta.isValido());
        assertEquals(3, respuesta.getCodigo()); // Código 3 = Expirado
        assertTrue(respuesta.getMensaje().toLowerCase().contains("expirado"));
        verify(dao, times(1)).marcarComoExpirado(104L);
    }

    @Test
    @DisplayName("Aislamiento estricto por tipo de transacción: un código de SPEI no puede autorizar TRASPASO")
    void testValidarCodigo_AislamientoPorTipo() {
        String usuario = "5512345678";

        // No hay código pendiente para TRASPASO
        when(dao.obtenerPendienteParaActualizacion(usuario, TipoTransaccionSeguridad.TRASPASO))
                .thenReturn(Optional.empty());

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRASPASO", "123456");
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuario, solicitud);

        assertFalse(respuesta.isValido());
        assertTrue(respuesta.getMensaje().contains("No existe un código de seguridad activo"));
    }

    @Test
    @DisplayName("Aislamiento estricto por usuario: un usuario no puede validar el código de otro")
    void testValidarCodigo_AislamientoPorUsuario() {
        String usuarioA = "5511111111";
        String usuarioB = "5522222222";

        when(dao.obtenerPendienteParaActualizacion(usuarioB, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI))
                .thenReturn(Optional.empty());

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", "123456");
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuarioB, solicitud);

        assertFalse(respuesta.isValido());
        verify(dao, never()).marcarComoUsado(anyLong(), anyString());
    }

    @Test
    @DisplayName("Protección contra reutilización: rechaza reuso de código que ya fue consumido")
    void testValidarCodigo_RechazaReutilizacion() {
        String usuario = "5512345678";
        String hash = passwordEncoder.encode("123456");

        CodigoTransaccion entity = new CodigoTransaccion(
                usuario, "cliente@banca.com", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                hash, "sol-uuid-1", 3, LocalDateTime.now(), LocalDateTime.now().plusMinutes(2)
        );
        entity.setId(105L);
        entity.setEstado("USADO"); // Ya fue usado

        when(dao.obtenerPendienteParaActualizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI))
                .thenReturn(Optional.of(entity));

        SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", "123456");
        RespuestaValidarCodigoDTO respuesta = service.validarCodigo(usuario, solicitud);

        assertFalse(respuesta.isValido());
        assertTrue(respuesta.getMensaje().contains("ya no es válido"));
    }

    @Test
    @DisplayName("Token de autorización: valida y consume token de autorización de un solo uso")
    void testValidarYConsumirTokenAutorizacion() {
        String usuario = "5512345678";
        String tokenAuth = "auth-token-xyz-123";

        when(dao.validarTokenAutorizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, tokenAuth))
                .thenReturn(true);
        when(dao.consumirTokenAutorizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, tokenAuth))
                .thenReturn(true);

        boolean valido = service.validarTokenAutorizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, tokenAuth);
        assertTrue(valido);

        boolean consumido = service.consumirTokenAutorizacion(usuario, TipoTransaccionSeguridad.TRANSFERENCIA_SPEI, tokenAuth);
        assertTrue(consumido);
    }

    @Test
    @DisplayName("Comportamiento ante solicitudes concurrentes: simulación de validación simultánea")
    void testValidacionConcurrente() throws InterruptedException {
        String usuario = "5512345678";
        String codigoPlano = "456789";
        String hash = passwordEncoder.encode(codigoPlano);

        // Simulamos el comportamiento de PostgreSQL FOR UPDATE y @Transactional
        final java.util.concurrent.locks.ReentrantLock dbLock = new java.util.concurrent.locks.ReentrantLock();
        final CodigoTransaccion entity = new CodigoTransaccion(
                usuario, "cliente@banca.com", TipoTransaccionSeguridad.TRANSFERENCIA_SPEI,
                hash, "sol-concurrente", 3, LocalDateTime.now(), LocalDateTime.now().plusMinutes(2)
        );
        entity.setId(200L);

        when(dao.obtenerPendienteParaActualizacion(eq(usuario), eq(TipoTransaccionSeguridad.TRANSFERENCIA_SPEI)))
                .thenAnswer(invocation -> {
                    // Simula adquisición de bloqueo de fila en BD (FOR UPDATE)
                    dbLock.lock();
                    if ("PENDIENTE".equals(entity.getEstado())) {
                        return Optional.of(entity);
                    } else {
                        dbLock.unlock();
                        return Optional.empty();
                    }
                });

        doAnswer(invocation -> {
            try {
                entity.setEstado("USADO");
            } finally {
                // Simula el commit de la transacción liberando el bloqueo
                if (dbLock.isHeldByCurrentThread()) {
                    dbLock.unlock();
                }
            }
            return null;
        }).when(dao).marcarComoUsado(eq(200L), anyString());

        int hilos = 5;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger fallos = new AtomicInteger(0);

        for (int i = 0; i < hilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    SolicitudValidarCodigoDTO solicitud = new SolicitudValidarCodigoDTO("TRANSFERENCIA_SPEI", codigoPlano);
                    RespuestaValidarCodigoDTO resp = service.validarCodigo(usuario, solicitud);
                    if (resp.isValido()) {
                        exitos.incrementAndGet();
                    } else {
                        fallos.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    if (dbLock.isHeldByCurrentThread()) {
                        dbLock.unlock();
                    }
                }
            });
        }

        latch.countDown(); // Despierta a todos los hilos simultáneamente
        executor.shutdown();
        executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);

        // Exactamente 1 hilo debe haber tenido éxito, los demás fallan porque el código ya no está PENDIENTE
        assertEquals(1, exitos.get(), "Exactamente una solicitud concurrente debió ser exitosa");
        assertEquals(hilos - 1, fallos.get(), "El resto de solicitudes concurrentes debieron ser rechazadas");
    }
}
