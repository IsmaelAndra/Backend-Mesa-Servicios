package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.TeamUser;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TeamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoutingServiceTest {

    private RoutingService routingService;
    private TeamUserRepository teamUserRepository;
    private RoutingConfigService routingConfigService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService();
        teamUserRepository = mock(TeamUserRepository.class);
        routingConfigService = mock(RoutingConfigService.class);
        ReflectionTestUtils.setField(routingService, "teamUserRepository", teamUserRepository);
        ReflectionTestUtils.setField(routingService, "routingConfigService", routingConfigService);
        when(routingConfigService.isAutoAssignEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("Round robin reparte los tickets entre todos los agentes en orden")
    void roundRobinAlternaEntreAgentes() {
        SupportTeam team = team("ROUND_ROBIN");
        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenReturn(List.of(membership(id(1), agent(id(1), "a"), null),
                        membership(id(2), agent(id(2), "b"), null),
                        membership(id(3), agent(id(3), "c"), null)));

        Category category = category(team);
        User primero = routingService.routeTicket(category);
        assertNotNull(primero);
        assertEquals(id(1), primero.getIdUser(), "sin asignaciones previas arranca por el primero");

        // Simula la marca de tiempo que hace el propio servicio.
        markAssigned(id(1), OffsetDateTime.now());

        User segundo = routingService.routeTicket(category);
        assertNotNull(segundo);
        assertEquals(id(2), segundo.getIdUser(), "después del último asignado va el siguiente");
    }

    @Test
    @DisplayName("El round robin marca la fecha del último agente asignado")
    void actualizaUltimaAsignacion() {
        SupportTeam team = team("ROUND_ROBIN");
        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenReturn(List.of(membership(id(1), agent(id(1), "a"), null),
                        membership(id(2), agent(id(2), "b"), null)));

        routingService.routeTicket(category(team));

        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(teamUserRepository).updateLastAssignedAt(id.capture(), any(OffsetDateTime.class));
        assertEquals(id(1), id.getValue());
    }

    @Test
    @DisplayName("Si la asignación automática está desactivada no se asigna a nadie")
    void respetaInterruptorGlobal() {
        when(routingConfigService.isAutoAssignEnabled()).thenReturn(false);
        SupportTeam team = team("ROUND_ROBIN");
        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenReturn(List.of(membership(id(1), agent(id(1), "a"), null)));

        assertNull(routingService.routeTicket(category(team)));
        verify(teamUserRepository, never()).updateLastAssignedAt(any(), any());
    }

    @Test
    @DisplayName("El método MANUAL deja el ticket en la cola sin asignar")
    void metodoManualNoAsigna() {
        SupportTeam team = team("MANUAL");
        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenReturn(List.of(membership(id(1), agent(id(1), "a"), null)));

        assertNull(routingService.routeTicket(category(team)));
    }

    @Test
    @DisplayName("Solo entran agentes activos, no eliminados y con rol AGENT")
    void filtraMiembrosNoElegibles() {
        SupportTeam team = team("ROUND_ROBIN");
        User admin = agent(id(9), "admin");
        admin.getRolUser().setNameRole("ADMIN");
        User inactivo = agent(id(8), "inactivo");
        inactivo.setStatusUser("INACTIVE");
        User eliminado = agent(id(7), "eliminado");
        eliminado.setDeletedUser(true);

        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenReturn(List.of(membership(id(1), admin, null),
                        membership(id(2), inactivo, null),
                        membership(id(3), eliminado, null)));

        assertNull(routingService.routeTicket(category(team)));
    }

    @Test
    @DisplayName("LEAST_ASSIGNED delega en el agente con menos carga")
    void usaAgenteConMenosCarga() {
        SupportTeam team = team("LEAST_ASSIGNED");
        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenReturn(List.of(membership(id(1), agent(id(1), "a"), null),
                        membership(id(2), agent(id(2), "b"), null)));
        when(teamUserRepository.findLeastAssigned(eq(id(1)), any(PageRequest.class)))
                .thenReturn(List.of(membership(id(2), agent(id(2), "b"), null)));

        User elegido = routingService.routeTicket(category(team));

        assertNotNull(elegido);
        assertEquals(id(2), elegido.getIdUser());
    }

    @Test
    @DisplayName("La rotación llega también al tercer agente (antes alternaba entre los dos primeros)")
    void roundRobinLlegaAlTercero() {
        SupportTeam team = team("ROUND_ROBIN");
        markAssigned(id(2), OffsetDateTime.now());
        User siguiente = routingService.routeTicket(category(team));
        assertEquals(id(3), siguiente.getIdUser(), "tras el segundo va el tercero");

        markAssigned(id(3), OffsetDateTime.now());
        assertEquals(id(1), routingService.routeTicket(category(team)).getIdUser(), "tras el último vuelve al primero");
    }

    @Test
    @DisplayName("Un equipo con todos los agentes añade a los que faltan, pero no a quien se quitó")
    void equipoConTodosLosAgentesSincroniza() {
        com.mesaayuda.repository.UserRepository userRepository = mock(com.mesaayuda.repository.UserRepository.class);
        ReflectionTestUtils.setField(routingService, "userRepository", userRepository);
        SupportTeam team = team("ROUND_ROBIN");
        team.setAutoMembersTeam(true);

        TeamUser quitado = membership(id(9), agent(id(2), "b"), null);
        quitado.setActiveTeamUser(false);
        when(teamUserRepository.findByTeamIdTeam(id(1)))
                .thenReturn(List.of(membership(id(8), agent(id(1), "a"), null), quitado));
        when(userRepository.findByRoleName("AGENT"))
                .thenReturn(List.of(agent(id(1), "a"), agent(id(2), "b"), agent(id(3), "c")));

        routingService.sincronizarAgentes(team);

        ArgumentCaptor<TeamUser> nuevo = ArgumentCaptor.forClass(TeamUser.class);
        verify(teamUserRepository, times(1)).save(nuevo.capture());
        assertEquals(id(3), nuevo.getValue().getUser().getIdUser(), "solo entra el agente que no tenía fila");
    }

    // ============================ helpers ============================

    /** El repositorio devuelve las membresías con la fecha ya persistida. */
    private void markAssigned(UUID idTeamUser, OffsetDateTime when) {
        when(teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(id(1)))
                .thenAnswer(inv -> List.of(
                        membership(id(1), agent(id(1), "a"), idTeamUser.equals(id(1)) ? when : null),
                        membership(id(2), agent(id(2), "b"), idTeamUser.equals(id(2)) ? when : null),
                        membership(id(3), agent(id(3), "c"), idTeamUser.equals(id(3)) ? when : null)));
    }

    private SupportTeam team(String method) {
        SupportTeam team = new SupportTeam();
        team.setIdTeam(id(1));
        team.setNameTeam("Mesa de Ayuda");
        team.setActiveTeam(true);
        team.setAssignmentMethod(method);
        return team;
    }

    private Category category(SupportTeam team) {
        Category category = new Category();
        category.setNameCategory("Hardware");
        category.setTeamCategory(team);
        return category;
    }

    private User agent(UUID id, String email) {
        Role role = new Role();
        role.setNameRole("AGENT");
        User user = new User();
        user.setIdUser(id);
        user.setCorporateEmailUser(email + "@coac.test");
        user.setStatusUser("ACTIVE");
        user.setDeletedUser(false);
        user.setRolUser(role);
        return user;
    }

    private TeamUser membership(UUID idTeamUser, User user, OffsetDateTime lastAssignedAt) {
        TeamUser tu = new TeamUser();
        tu.setIdTeamUser(idTeamUser);
        tu.setUser(user);
        tu.setActiveTeamUser(true);
        tu.setLastAssignedAt(lastAssignedAt);
        return tu;
    }
}
