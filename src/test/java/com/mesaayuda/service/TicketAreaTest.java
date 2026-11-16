package com.mesaayuda.service;

import com.mesaayuda.dto.TicketRequest;
import com.mesaayuda.dto.TrasladoRequest;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.AttachRepository;
import com.mesaayuda.repository.CategoryRepository;
import com.mesaayuda.repository.PriorityRepository;
import com.mesaayuda.repository.StatusRepository;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketTypeRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mesa multi-área (V28): el ticket tiene un área que lo atiende, que sale de la
 * categoría, y se puede trasladar a otra área con motivo.
 */
@ExtendWith(MockitoExtension.class)
class TicketAreaTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private RoutingService routingService;
    @Mock private SlaService slaService;
    @Mock private AttachRepository attachRepository;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;
    @Mock private AuditService auditService;
    @Mock private TicketMapper ticketMapper;
    @Mock private TicketAccessPolicy accessPolicy;

    @InjectMocks private TicketService ticketService;

    private Area tecnologia;
    private Area desarrollo;
    private Category soporte;
    private Category sistemas;
    private User actor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ticketService, "uploadDir", "target/test-uploads");
        ReflectionTestUtils.setField(ticketService, "sseEventBroker", mock(SseEventBroker.class));
        tecnologia = area(id(1), "Tecnología");
        desarrollo = area(id(2), "Desarrollo");
        soporte = categoria(id(11), "Soporte Técnico", tecnologia);
        sistemas = categoria(id(12), "Sistemas internos", desarrollo);

        actor = new User();
        actor.setIdUser(id(90));
        actor.setCorporateEmailUser("jefe@coac.test");
        lenient().when(userService.getCurrentUser()).thenReturn(actor);
        lenient().when(accessPolicy.esPersonal(any())).thenReturn(true);
        lenient().when(accessPolicy.puedeEscribir(any(), any())).thenReturn(true);

        lenient().when(statusRepository.findByNameStatus(any())).thenAnswer(inv -> Optional.of(estado(inv.getArgument(0))));
        lenient().when(ticketTypeRepository.findTopByDeletedTicketTypeFalseOrderByCreatedAtTicketTypeAsc())
                .thenReturn(Optional.empty());
        lenient().when(priorityRepository.findByLevelPriority(any(Integer.class)))
                .thenReturn(Optional.of(mock(Priority.class)));
        lenient().when(areaRepository.findById(tecnologia.getIdArea())).thenReturn(Optional.of(tecnologia));
        lenient().when(areaRepository.findById(desarrollo.getIdArea())).thenReturn(Optional.of(desarrollo));
        lenient().when(categoryRepository.findById(soporte.getIdCategory())).thenReturn(Optional.of(soporte));
        lenient().when(categoryRepository.findById(sistemas.getIdCategory())).thenReturn(Optional.of(sistemas));
        lenient().when(categoryRepository
                        .findFirstByAreaCategoryIdAreaAndDeletedCategoryFalseOrderByCreatedAtCategoryAsc(desarrollo.getIdArea()))
                .thenReturn(Optional.of(sistemas));
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(routingService.routeTicket(any(Category.class))).thenReturn(null);
    }

    private static Area area(UUID id, String nombre) {
        Area a = new Area();
        a.setIdArea(id);
        a.setNameArea(nombre);
        a.setDeletedArea(false);
        return a;
    }

    private static Category categoria(UUID id, String nombre, Area area) {
        Category c = new Category();
        c.setIdCategory(id);
        c.setNameCategory(nombre);
        c.setDeletedCategory(false);
        c.setAreaCategory(area);
        SupportTeam equipo = new SupportTeam();
        equipo.setIdTeam(UUID.randomUUID());
        equipo.setAreaTeam(area);
        c.setTeamCategory(equipo);
        return c;
    }

    private static Status estado(String nombre) {
        Status s = new Status();
        s.setNameStatus(nombre);
        s.setDeletedStatus(false);
        return s;
    }

    private Ticket crear(TicketRequest req) {
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(captor.capture())).thenAnswer(inv -> captor.getValue());
        ticketService.create(req);
        return captor.getValue();
    }

    private Ticket ticketDeTecnologia(String estado) {
        Ticket t = new Ticket();
        t.setIdTicket(id(100));
        t.setNumberTicket(43L);
        t.setDeletedTicket(false);
        t.setAreaDestinoTicket(tecnologia);
        t.setCategoryTicket(soporte);
        t.setTeamTicket(soporte.getTeamCategory());
        User agente = new User();
        agente.setIdUser(id(91));
        agente.setCorporateEmailUser("agente@coac.test");
        t.setAgentTicket(agente);
        t.setStatusTicket(estado(estado));
        when(ticketRepository.findById(t.getIdTicket())).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("el área que atiende sale de la categoría")
    void elAreaSaleDeLaCategoria() {
        Ticket t = crear(new TicketRequest("Falla", "No arranca", soporte.getIdCategory(), null, null, null));
        assertEquals(tecnologia, t.getAreaDestinoTicket());
    }

    @Test
    @DisplayName("sin categoría se usa la primera del área elegida")
    void sinCategoriaSeUsaLaPrimeraDelArea() {
        Ticket t = crear(new TicketRequest("Reporte", "Nuevo reporte", null, null, null, null, null, null,
                desarrollo.getIdArea()));
        assertEquals(desarrollo, t.getAreaDestinoTicket());
        assertEquals(sistemas, t.getCategoryTicket());
    }

    @Test
    @DisplayName("una categoría de otra área se rechaza")
    void categoriaDeOtraAreaSeRechaza() {
        assertThrows(BadRequestException.class, () -> ticketService.create(new TicketRequest("x", "y",
                soporte.getIdCategory(), null, null, null, null, null, desarrollo.getIdArea())));
    }

    @Test
    @DisplayName("trasladar cambia el área, quita el agente, vuelve a NUEVO y queda en el historial")
    void trasladarCambiaElArea() {
        Ticket t = ticketDeTecnologia("ASIGNADO");

        ticketService.trasladar(t.getIdTicket(), new TrasladoRequest(desarrollo.getIdArea(), null, "Es un error del sistema"));

        assertEquals(desarrollo, t.getAreaDestinoTicket());
        assertEquals(sistemas, t.getCategoryTicket());
        assertEquals(sistemas.getTeamCategory(), t.getTeamTicket());
        assertNull(t.getAgentTicket());
        assertEquals("NUEVO", t.getStatusTicket().getNameStatus());
        verify(auditService).record(eq(t), eq("AREA_TRANSFER"), eq("Tecnología"), eq("Desarrollo"),
                startsWith("Motivo: Es un error del sistema"));
        verify(routingService).routeTicket(sistemas);
    }

    @Test
    @DisplayName("no se traslada a la misma área")
    void noSeTrasladaALaMismaArea() {
        Ticket t = ticketDeTecnologia("ASIGNADO");
        assertThrows(BadRequestException.class, () ->
                ticketService.trasladar(t.getIdTicket(), new TrasladoRequest(tecnologia.getIdArea(), null, "motivo")));
        verify(auditService, never()).record(any(), eq("AREA_TRANSFER"), any(), any(), any());
    }

    @Test
    @DisplayName("un ticket cerrado no se traslada")
    void unTicketCerradoNoSeTraslada() {
        Ticket t = ticketDeTecnologia("CERRADO");
        assertThrows(BadRequestException.class, () ->
                ticketService.trasladar(t.getIdTicket(), new TrasladoRequest(desarrollo.getIdArea(), null, "motivo")));
    }

    @Test
    @DisplayName("sin permiso de gestión no se traslada")
    void sinPermisoNoSeTraslada() {
        Ticket t = ticketDeTecnologia("ASIGNADO");
        when(accessPolicy.puedeEscribir(any(), any())).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                ticketService.trasladar(t.getIdTicket(), new TrasladoRequest(desarrollo.getIdArea(), null, "motivo")));
    }
}
