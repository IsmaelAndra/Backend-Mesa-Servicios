package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El estado de sesión combina dos señales: la conexión SSE viva y la última
 * renovación del token. Estos tests fijan el comportamiento de los tres estados.
 */
class PresenceServiceTest {

    private PresenceService service;
    private SseEventBroker sseEventBroker;
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        service = new PresenceService();
        sseEventBroker = Mockito.mock(SseEventBroker.class);
        refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
        ReflectionTestUtils.setField(service, "sseEventBroker", sseEventBroker);
        ReflectionTestUtils.setField(service, "refreshTokenRepository", refreshTokenRepository);
        ReflectionTestUtils.setField(service, "accessTokenMs", 900_000L);
    }

    private void conectado(UUID id, boolean valor) {
        Mockito.when(sseEventBroker.isConectado(id)).thenReturn(valor);
    }

    private void actividad(Map<UUID, OffsetDateTime> filas) {
        List<Object[]> respuesta = filas.entrySet().stream()
                .map(e -> new Object[] { e.getKey(), e.getValue() })
                .toList();
        Mockito.when(refreshTokenRepository.ultimaActividadPorUsuario(
                Mockito.anyCollection(), Mockito.any(OffsetDateTime.class)))
                .thenReturn(respuesta);
    }

    @Test
    void conConexionSseEsEnLinea() {
        conectado(id(1), true);
        actividad(Map.of(id(1), OffsetDateTime.now().minusMinutes(60)));

        assertEquals(PresenceService.EN_LINEA, service.estadoDe(id(1)));
    }

    @Test
    void sinConexionPeroConTokenRecienteEsReciente() {
        conectado(id(2), false);
        actividad(Map.of(id(2), OffsetDateTime.now().minusMinutes(5)));

        assertEquals(PresenceService.RECIENTE, service.estadoDe(id(2)));
    }

    @Test
    void sinConexionYSinActividadRecienteEsDesconectado() {
        conectado(id(3), false);
        actividad(Map.of(id(3), OffsetDateTime.now().minusMinutes(40)));

        assertEquals(PresenceService.DESCONECTADO, service.estadoDe(id(3)));
    }

    @Test
    void sinNingunTokenEsDesconectado() {
        conectado(id(4), false);
        actividad(Map.of());

        assertEquals(PresenceService.DESCONECTADO, service.estadoDe(id(4)));
    }

    @Test
    void unIdNuloEsDesconectado() {
        assertEquals(PresenceService.DESCONECTADO, service.estadoDe(null));
    }

    @Test
    void resuelveVariosUsuariosEnUnaSolaConsulta() {
        Mockito.when(sseEventBroker.isConectado(id(10))).thenReturn(true);
        Mockito.when(sseEventBroker.isConectado(id(11))).thenReturn(false);
        Mockito.when(sseEventBroker.isConectado(id(12))).thenReturn(false);
        actividad(Map.of(
                id(11), OffsetDateTime.now().minusMinutes(3),
                id(12), OffsetDateTime.now().minusHours(5)));

        Map<UUID, String> estados = service.estadosDe(List.of(id(10), id(11), id(12)));

        assertEquals(PresenceService.EN_LINEA, estados.get(id(10)));
        assertEquals(PresenceService.RECIENTE, estados.get(id(11)));
        assertEquals(PresenceService.DESCONECTADO, estados.get(id(12)));
    }

    @Test
    void laListaVaciaNoConsultaNada() {
        assertEquals(0, service.estadosDe(List.of()).size());
        Mockito.verify(refreshTokenRepository, Mockito.never()).ultimaActividadPorUsuario(
                Mockito.anyCollection(), Mockito.any(OffsetDateTime.class));
    }
}
