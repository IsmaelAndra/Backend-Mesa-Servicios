package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.GlobalSearchResponse;
import com.mesaayuda.dto.SearchUserItem;
import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.repository.SupportTeamRepository;
import com.mesaayuda.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Búsqueda global: qué grupos ve cada rol y qué llega a cada repositorio.
 *
 * <p>La política de tickets no se prueba aquí (la cubre TicketServiceTest); aquí
 * se comprueba el corte por rol del propio SearchService: un USER no puede pedir
 * personas ni workflows, un AGENT no puede pedir workflows y solo SUPER_ADMIN
 * los ve.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private TicketService ticketService;
    @Mock private UserService userService;
    @Mock private TicketAccessPolicy accessPolicy;
    @Mock private AreaRepository areaRepository;
    @Mock private SupportTeamRepository supportTeamRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetService assetService;
    @Mock private WorkflowRepository workflowRepository;

    @InjectMocks private SearchService searchService;

    /** El alcance de activos lo da AssetService; aquí basta un Specification vacío. */
    @org.junit.jupiter.api.BeforeEach
    void alcanceDeActivos() {
        lenient().when(assetService.alcanceVisible(any())).thenReturn((root, query, cb) -> null);
    }

    private User usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioConRol("USER");
        when(userService.getCurrentUser()).thenReturn(usuario);
    }

    private User usuarioConRol(String rol) {
        User u = new User();
        u.setIdUser(id(1));
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    private void como(String rol) {
        Role r = new Role();
        r.setNameRole(rol);
        usuario.setRolUser(r);
    }

    private TicketResponse ticketResponse() {
        return new TicketResponse(id(5), id(1), "Impresora", "no imprime", "juan@coac.local",
                "Juan", null, null, null, "ana@coac.local", "Soporte", "Sistemas",
                null, null, "Hardware", "SOLICITUD", "NUEVO", "ALTA", 1,
                null, OffsetDateTime.now(), OffsetDateTime.now(), null);
    }

    /** Stubs comunes: un ticket, un equipo y un área. */
    private void datosDeApoyo() {
        SupportTeam equipo = new SupportTeam();
        equipo.setIdTeam(id(7));
        equipo.setNameTeam("Soporte");
        when(supportTeamRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(equipo)));

        Area area = new Area();
        area.setIdArea(id(3));
        area.setNameArea("Sistemas");
        when(areaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(area)));

        // Los activos se consultan para todos los roles, con el alcance que da
        // AssetService (aquí un Specification vacío: el repositorio es un mock).
        lenient().when(assetRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        when(ticketService.search(any(), any(), any(), any(), any(), any(),
                any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageImpl<>(List.of(ticketResponse())));
    }

    /**
     * Personas por cada rol. La búsqueda consulta primero el grupo USER y luego
     * el AGENT, así que el stub se entrega en ese orden (dos llamadas).
     */
    private void conPersonas(List<User> paraUsuarios, List<User> paraAgentes) {
        when(userService.findAllPorSpec(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(paraUsuarios), new PageImpl<>(paraAgentes));
    }

    /** Equipos, áreas y tickets vacíos, para los tests que solo miran grupos. */
    private void sinTicketsNiEquiposNiAreas() {
        when(supportTeamRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(areaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(assetRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(ticketService.search(any(), any(), any(), any(), any(), any(),
                any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private User persona(String rol, UUID id, String correo, String nombre) {
        User u = usuarioConRol(rol);
        u.setIdUser(id);
        u.setCorporateEmailUser(correo);
        u.setDisplayName(nombre);
        return u;
    }

    private User agenteAna() {
        return persona("AGENT", id(10), "ana@coac.local", "Ana Lopez");
    }

    private User usuarioJuan() {
        return persona("USER", id(2), "juan@coac.local", "Juan Perez");
    }

    @Test
    void unSolicitanteSoloVeSusTicketsEquiposYAreas() {
        como("USER");
        when(accessPolicy.esPersonal(usuario)).thenReturn(false);
        datosDeApoyo();

        GlobalSearchResponse r = searchService.buscar("impresora");

        assertTrue(r.tickets().stream().anyMatch(t -> t.idTicket().equals(id(5))));
        assertTrue(r.teams().stream().anyMatch(t -> t.idTeam().equals(id(7))));
        assertTrue(r.areas().stream().anyMatch(a -> a.idArea().equals(id(3))));
        assertTrue(r.users().isEmpty());
        assertTrue(r.agents().isEmpty());
        assertTrue(r.workflows().isEmpty());
        assertTrue(r.assets().isEmpty());
        verify(userService, never()).findAllPorSpec(any(), any());
        verify(workflowRepository, never()).buscarPorTexto(any(), any());
    }

    @Test
    void unSolicitanteBuscaSoloEntreSusActivos() {
        como("USER");
        when(accessPolicy.esPersonal(usuario)).thenReturn(false);
        datosDeApoyo();

        searchService.buscar("laptop");

        // El corte lo pone AssetService.alcanceVisible: el mismo que el listado.
        verify(assetService).alcanceVisible(usuario);
    }

    @Test
    void unAgenteVePersonasPeroNoWorkflows() {
        como("AGENT");
        when(accessPolicy.esPersonal(usuario)).thenReturn(true);
        conPersonas(List.of(), List.of(agenteAna()));
        datosDeApoyo();

        GlobalSearchResponse r = searchService.buscar("ana");

        assertTrue(r.agents().stream().anyMatch(a -> a.corporateEmailUser().equals("ana@coac.local")));
        assertTrue(r.users().isEmpty());
        assertTrue(r.workflows().isEmpty());
        verify(userService, org.mockito.Mockito.times(2)).findAllPorSpec(any(), any());
        verify(workflowRepository, never()).buscarPorTexto(any(), any());
    }

    @Test
    void soloSuperAdminVeWorkflows() {
        como("SUPER_ADMIN");
        when(accessPolicy.esPersonal(usuario)).thenReturn(true);
        conPersonas(List.of(usuarioJuan()), List.of());
        Workflow wf = new Workflow();
        wf.setIdWorkflow(id(9));
        wf.setNameWorkflow("Cierre de ticket");
        wf.setStatusWorkflow("ACTIVO");
        when(workflowRepository.buscarPorTexto(any(), any()))
                .thenReturn(new PageImpl<>(List.of(wf)));
        datosDeApoyo();

        GlobalSearchResponse r = searchService.buscar("cierre");

        assertTrue(r.workflows().stream().anyMatch(w -> w.idWorkflow().equals(id(9))));
        assertTrue(r.users().stream().anyMatch(u -> u.corporateEmailUser().equals("juan@coac.local")));
    }

    @Test
    void lasPersonasSeSirvenConElNombreMostradoYElArea() {
        como("ADMIN");
        when(accessPolicy.esPersonal(usuario)).thenReturn(true);
        User persona = usuarioJuan();
        Area area = new Area();
        area.setNameArea("Cajas");
        persona.setArea(area);
        conPersonas(List.of(persona), List.of());
        datosDeApoyo();

        GlobalSearchResponse r = searchService.buscar("juan");

        assertTrue(r.users().stream().anyMatch(u -> u.idUser().equals(id(2))));
        SearchUserItem item = r.users().get(0);
        assertEquals("Juan Perez", item.displayName());
        assertEquals("USER", item.role());
        assertEquals("Cajas", item.area());
    }

    @Test
    void losGruposVaciosLleganComoListaVacia() {
        como("SUPER_ADMIN");
        when(accessPolicy.esPersonal(usuario)).thenReturn(true);
        conPersonas(List.of(), List.of());
        sinTicketsNiEquiposNiAreas();
        when(workflowRepository.buscarPorTexto(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        GlobalSearchResponse r = searchService.buscar("xyz");

        assertTrue(r.tickets().isEmpty());
        assertTrue(r.teams().isEmpty());
        assertTrue(r.areas().isEmpty());
        assertTrue(r.users().isEmpty());
        assertTrue(r.agents().isEmpty());
        assertTrue(r.workflows().isEmpty());
        assertTrue(r.assets().isEmpty());
        assertEquals("xyz", r.query());
    }

    @Test
    void lasConsultasPIDenUnLimiteDeCincoPorGrupo() {
        como("AGENT");
        when(accessPolicy.esPersonal(usuario)).thenReturn(true);
        conPersonas(List.of(), List.of());
        sinTicketsNiEquiposNiAreas();

        searchService.buscar("a");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(supportTeamRepository).findAll(any(Specification.class), captor.capture());
        verify(areaRepository).findAll(any(Specification.class), captor.capture());
        verify(userService, org.mockito.Mockito.times(2)).findAllPorSpec(any(Specification.class), captor.capture());
        assertEquals(SearchService.LIMITE_POR_GRUPO, 5);
        captor.getAllValues().forEach(p -> assertEquals(SearchService.LIMITE_POR_GRUPO, p.getPageSize()));
    }

    @Test
    void elTerminoLlegaEscapadoALasConsultas() {
        como("SUPER_ADMIN");
        when(accessPolicy.esPersonal(usuario)).thenReturn(true);
        conPersonas(List.of(), List.of());
        sinTicketsNiEquiposNiAreas();
        when(workflowRepository.buscarPorTexto(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        searchService.buscar("100%");

        verify(ticketService).search(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("100\\%"), any(), any(Integer.class), any(Integer.class));
        verify(workflowRepository).buscarPorTexto(
                org.mockito.ArgumentMatchers.eq("100\\%"), any(Pageable.class));
    }
}