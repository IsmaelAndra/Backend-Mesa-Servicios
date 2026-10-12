package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.MonthlyEvaluation;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.MonthlyEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;

/**
 * La tabla de evaluaciones mensuales muestra a cada agente con su foto y su punto
 * de sesión, así que el DTO tiene que traer ambas cosas. Antes solo mandaba el
 * correo y la pantalla se veía obligada a pintar la inicial y nada más.
 *
 * El estado de sesión se pide en lote: si se pidiera uno a uno, la pantalla
 * abriría una consulta por agente, y la tabla crece con el padrón.
 */
class EvaluationServiceResponseTest {

    private static final LocalDate MES = LocalDate.of(2026, 9, 1);
    private static final UUID AGENTE = id(7);

    private EvaluationService service;
    private MonthlyEvaluationRepository evaluationRepository;
    private UserService userService;
    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        service = new EvaluationService();
        evaluationRepository = Mockito.mock(MonthlyEvaluationRepository.class);
        userService = Mockito.mock(UserService.class);
        presenceService = Mockito.mock(PresenceService.class);

        ReflectionTestUtils.setField(service, "evaluationRepository", evaluationRepository);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "presenceService", presenceService);

        // findByMonth exige administrador: sin esto la prueba midiaria un 403.
        Mockito.when(userService.getCurrentUser()).thenReturn(usuarioActual("SUPER_ADMIN"));
        Mockito.when(evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(MES))
                .thenReturn(List.of());
        Mockito.when(presenceService.estadosDe(anyCollection())).thenReturn(Map.of());
    }

    private static User usuarioActual(String rol) {
        User u = new User();
        u.setIdUser(id(1));
        u.setCorporateEmailUser("admin@coacmagisterio.fin.ec");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    private static User agente(String displayName, String photo) {
        User u = new User();
        u.setIdUser(AGENTE);
        u.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        u.setDisplayName(displayName);
        u.setPhotoUrl(photo);
        return u;
    }

    private static MonthlyEvaluation evaluacion(User agente) {
        MonthlyEvaluation ev = new MonthlyEvaluation();
        ev.setIdEvaluation(id(1));
        ev.setAgent(agente);
        ev.setEvaluationMonth(MES);
        ev.setStatus("COMPLETED");
        return ev;
    }

    @Test
    @DisplayName("la evaluación trae nombre, foto y estado de sesión del agente")
    void traeNombreFotoYSesion() {
        Mockito.when(evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(MES))
                .thenReturn(List.of(evaluacion(agente("Ismael Andrade", "/uploads/fotos/7.png"))));
        Mockito.when(presenceService.estadosDe(anyCollection()))
                .thenReturn(Map.of(AGENTE, PresenceService.EN_LINEA));

        var resp = service.findByMonth(MES).get(0);

        assertThat(resp.agentDisplayName()).isEqualTo("Ismael Andrade");
        assertThat(resp.agentPhotoUrl()).isEqualTo("/uploads/fotos/7.png");
        assertThat(resp.agentSessionState()).isEqualTo(PresenceService.EN_LINEA);
    }

    @Test
    @DisplayName("sin foto se manda cadena vacía y no null, para no romper el binding")
    void sinFotoMandaCadenaVacia() {
        Mockito.when(evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(MES))
                .thenReturn(List.of(evaluacion(agente("Ismael Andrade", null))));

        var resp = service.findByMonth(MES).get(0);

        assertThat(resp.agentPhotoUrl()).isEmpty();
    }

    @Test
    @DisplayName("si el estado de sesión no viene, se trata como desconectado")
    void estadoAusenteEsDesconectado() {
        Mockito.when(evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(MES))
                .thenReturn(List.of(evaluacion(agente("Ismael Andrade", null))));
        // El agente no aparece en el mapa: todavia no hay informacion de sesion.
        Mockito.when(presenceService.estadosDe(anyCollection())).thenReturn(Map.of());

        var resp = service.findByMonth(MES).get(0);

        assertThat(resp.agentSessionState()).isEqualTo(PresenceService.DESCONECTADO);
    }

    @Test
    @DisplayName("el nombre cae a la parte del correo cuando no hay nada mas")
    void nombreCaeAParteDelCorreo() {
        Mockito.when(evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(MES))
                .thenReturn(List.of(evaluacion(agente(null, null))));

        var resp = service.findByMonth(MES).get(0);

        assertThat(resp.agentDisplayName()).isEqualTo("agente");
    }

    @Test
    @DisplayName("la presencia de toda la tabla se pide en una sola llamada, no una por agente")
    void pideLaPresenciaEnLote() {
        User otro = agente("Otra Agente", null);
        otro.setIdUser(id(8));
        Mockito.when(evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(MES))
                .thenReturn(List.of(evaluacion(agente("Ismael Andrade", null)), evaluacion(otro)));
        Mockito.when(presenceService.estadosDe(anyCollection()))
                .thenReturn(Map.of(AGENTE, PresenceService.EN_LINEA, id(8), PresenceService.RECIENTE));

        var resp = service.findByMonth(MES);

        assertThat(resp).hasSize(2);
        assertThat(resp.get(0).agentSessionState()).isEqualTo(PresenceService.EN_LINEA);
        assertThat(resp.get(1).agentSessionState()).isEqualTo(PresenceService.RECIENTE);
        // Una sola consulta para las dos filas: el repositorio se pregunta una vez.
        verify(presenceService, Mockito.times(1)).estadosDe(any());
        verify(evaluationRepository, Mockito.times(1))
                .findByEvaluationMonthOrderBySatisfactionScoreDesc(MES);
    }
}
