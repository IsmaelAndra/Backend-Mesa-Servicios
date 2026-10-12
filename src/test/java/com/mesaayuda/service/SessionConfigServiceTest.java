package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * La duracion de la sesion se configura desde la pantalla de seguridad. Estos
 * tests fijan que un valor absurdo no deje la app con un token inservible ni con
 * una sesion eterna.
 */
class SessionConfigServiceTest {

    private SessionConfigService service;
    private com.mesaayuda.repository.SessionConfigRepository repository;

    @BeforeEach
    void setUp() {
        service = new SessionConfigService();
        repository = Mockito.mock(com.mesaayuda.repository.SessionConfigRepository.class);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "defaultExpirationMs", 900_000L);
    }

    @Test
    void sinFilaEnLaBaseUsaElValorDelYml() {
        when(repository.findTopByOrderByIdSessionConfigAsc()).thenReturn(java.util.Optional.empty());

        assertEquals(Duration.ofMinutes(15), service.duracion());
        assertEquals(5, service.warningMinutos());
        assertTrue(service.autoRenew());
    }

    @Test
    void unaDuracionMenorAlPisoSeAcota() {
        configCon(2, 5, true);

        // Con 2 minutos el token se caeria casi al instante y el usuario
        // perderia la sesion en cada accion.
        assertEquals(Duration.ofMinutes(5), service.duracion());
    }

    @Test
    void unaDuracionExcesivaSeAcota() {
        configCon(99999, 5, true);

        // Un token de 69 dias no es una sesion, es una credencial sin caducidad.
        assertEquals(Duration.ofMinutes(1440), service.duracion());
    }

    @Test
    void elAvisoNoPuedeSerCero() {
        configCon(30, 0, true);

        assertEquals(1, service.warningMinutos());
    }

    @Test
    void laDuracionConfiguradaSeRespeta() {
        configCon(45, 10, true);

        assertEquals(Duration.ofMinutes(45), service.duracion());
        assertEquals(10, service.warningMinutos());
    }

    @Test
    void guardarInvalidaLaCacheParaQueElSiguienteLoginUseElValorNuevo() {
        configCon(15, 5, true);
        assertEquals(Duration.ofMinutes(15), service.duracion());

        com.mesaayuda.entity.SessionConfig nuevo = config(30, 5, true);
        when(repository.save(any())).thenReturn(nuevo);
        service.actualizar(30, 5, true, null, null, id(1));

        assertEquals(Duration.ofMinutes(30), service.duracion());
    }

    @Test
    void laInactividadSePuedeApagar() {
        com.mesaayuda.entity.SessionConfig c = config(15, 5, true);
        c.setIdleEnabled(false);
        c.setIdleMinutes(30);
        when(repository.findTopByOrderByIdSessionConfigAsc()).thenReturn(java.util.Optional.of(c));
        service.invalidarCache();

        assertFalse(service.idleHabilitado());
        // Aun apagada se conserva el valor, por si se vuelve a encender.
        assertEquals(30, service.idleMinutos());
    }

    @Test
    void laInactividadVieneActivaPorDefecto() {
        configCon(15, 5, true);

        assertTrue(service.idleHabilitado());
        assertEquals(15, service.idleMinutos());
    }

    @Test
    void elTiempoDeInactividadSeAcotaParaNoDejarLaSesionEterna() {
        configCon(15, 5, true);
        assertEquals(15, service.idleMinutos());

        com.mesaayuda.entity.SessionConfig exaggerated = config(15, 5, true);
        exaggerated.setIdleMinutes(99_999);
        when(repository.findTopByOrderByIdSessionConfigAsc())
                .thenReturn(java.util.Optional.of(exaggerated));
        service.invalidarCache();

        assertEquals(1440, service.idleMinutos());
    }

    @Test
    void guardarGuardaElTiempoDeInactividad() {
        configCon(15, 5, true);

        com.mesaayuda.entity.SessionConfig nuevo = config(15, 5, true);
        nuevo.setIdleEnabled(true);
        nuevo.setIdleMinutes(45);
        when(repository.save(any())).thenReturn(nuevo);
        service.actualizar(null, null, null, true, 45, id(1));

        assertTrue(service.idleHabilitado());
        assertEquals(45, service.idleMinutos());
    }

    private void configCon(int minutos, int aviso, boolean autoRenew) {
        when(repository.findTopByOrderByIdSessionConfigAsc())
                .thenReturn(java.util.Optional.of(config(minutos, aviso, autoRenew)));
        service.invalidarCache();
    }

    private com.mesaayuda.entity.SessionConfig config(int minutos, int aviso, boolean autoRenew) {
        com.mesaayuda.entity.SessionConfig c = new com.mesaayuda.entity.SessionConfig();
        c.setIdSessionConfig(id(1));
        c.setSessionMinutes(minutos);
        c.setWarningMinutes(aviso);
        c.setAutoRenew(autoRenew);
        c.setIdleEnabled(true);
        c.setIdleMinutes(15);
        return c;
    }
}
