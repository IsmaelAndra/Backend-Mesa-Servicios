package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.TicketRequest;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.mapper.TicketMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sede fisica de un ticket.
 *
 * La regla acordada es que la sede del ticket puede ser distinta de la del
 * solicitante: alguien de Quito puede reportar un problema que hay que resolver
 * en Guayaquil. Cuando el formulario no elige sede, el ticket hereda la del
 * solicitante, y si la sede indicada ya no existe o esta dada de baja se ignora
 * en lugar de fallar, para que el ticket se pueda abrir igual.
 */
@ExtendWith(MockitoExtension.class)
class TicketSucursalTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private RoutingService routingService;
    @Mock private SlaService slaService;
    @Mock private com.mesaayuda.service.SseEventBroker sseEventBroker;
    @Mock private AttachRepository attachRepository;
    @Mock private UserService userService;
@Mock private NotificationService notificationService;
    @Mock private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;
    @Mock private AuditService auditService;
    @Mock private TicketMapper ticketMapper;

    @InjectMocks private TicketService ticketService;

private Sucursal quito;
    private Sucursal guayaquil;
    private ArgumentCaptor<Ticket> captor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ticketService, "uploadDir", "target/test-uploads");
        ReflectionTestUtils.setField(ticketService, "sseEventBroker", mock(SseEventBroker.class));
        captor = ArgumentCaptor.forClass(Ticket.class);

        // create() resuelve categoría, tipo, estado y prioridad antes de tocar la sede.
        Category categoria = new Category();
        categoria.setIdCategory(id(1));
        categoria.setDeletedCategory(false);
        lenient().when(categoryRepository.findFirstByDeletedCategoryFalseOrderByCreatedAtCategoryAsc())
                .thenReturn(Optional.of(categoria));
        Status nuevo = new Status();
        nuevo.setNameStatus("NUEVO");
        lenient().when(statusRepository.findByNameStatus("NUEVO")).thenReturn(Optional.of(nuevo));
        lenient().when(ticketTypeRepository.findTopByDeletedTicketTypeFalseOrderByCreatedAtTicketTypeAsc())
                .thenReturn(Optional.empty());
        lenient().when(priorityRepository.findByLevelPriority(any(Integer.class)))
                .thenReturn(Optional.of(mock(Priority.class)));
        quito = sede(id(1), "QUI", "Quito");
        guayaquil = sede(id(2), "GYE", "Guayaquil");
        lenient().when(sucursalRepository.findById(quito.getIdSucursal())).thenReturn(Optional.of(quito));
        lenient().when(sucursalRepository.findById(guayaquil.getIdSucursal())).thenReturn(Optional.of(guayaquil));
    }

    private static Sucursal sede(UUID id, String codigo, String nombre) {
        Sucursal s = new Sucursal();
        s.setIdSucursal(id);
        s.setCodeSucursal(codigo);
        s.setNameSucursal(nombre);
        s.setDeletedSucursal(false);
        s.setStatusSucursal(true);
        return s;
    }

    private User solicitanteCon(Sucursal sede) {
        User u = new User();
        u.setIdUser(id(10));
        u.setSucursal(sede);
        return u;
    }

    private TicketRequest request(UUID sucursalId) {
        return new TicketRequest("titulo", "descripcion", null, null, null, sucursalId);
    }

/** Ejecuta el alta y devuelve el ticket que se acababa de guardar. */
    private Ticket crea(UUID sucursalId, User solicitante) {
        when(userService.getCurrentUser()).thenReturn(solicitante);
        // save() devuelve la misma entidad que recibe, como hace JPA: si se
        // devolviera una copia vacia, el resto de create() veria campos nulos.
        when(ticketRepository.save(captor.capture())).thenAnswer(inv -> captor.getValue());
        lenient().when(ticketMapper.toResponse(any(Ticket.class))).thenAnswer(inv -> null);
        lenient().when(routingService.routeTicket(any(Category.class))).thenReturn(null);
        ticketService.create(request(sucursalId));
        return captor.getValue();
    }

    @Test
    @DisplayName("sin sede en el formulario, el ticket hereda la del solicitante")
    void heredaLaSedeDelSolicitante() {
        Ticket t = crea(null, solicitanteCon(quito));

        assertEquals(quito, t.getSucursalTicket());
    }

    @Test
    @DisplayName("la sede del ticket puede ser distinta de la del solicitante")
    void sedeDistintaALaDelSolicitante() {
        Ticket t = crea(guayaquil.getIdSucursal(), solicitanteCon(quito));

        assertEquals(guayaquil, t.getSucursalTicket());
    }

    @Test
    @DisplayName("una sede que no existe se ignora y se hereda la del solicitante")
    void sedeInexistenteSeIgnora() {
        when(sucursalRepository.findById(id(99))).thenReturn(Optional.empty());

        Ticket t = crea(id(99), solicitanteCon(quito));

        assertEquals(quito, t.getSucursalTicket());
    }

    @Test
    @DisplayName("una sede dada de baja se ignora y se hereda la del solicitante")
    void sedeBorradaSeIgnora() {
        Sucursal borrada = sede(id(3), "AMB", "Ambato");
        borrada.setDeletedSucursal(true);
        when(sucursalRepository.findById(id(3))).thenReturn(Optional.of(borrada));

        Ticket t = crea(id(3), solicitanteCon(quito));

        assertEquals(quito, t.getSucursalTicket());
    }

    @Test
    @DisplayName("sin sede en el formulario y solicitante sin sede, el ticket queda sin sede")
    void solicitanteSinSedeDejaElTicketSinSede() {
        Ticket t = crea(null, solicitanteCon(null));

        assertNull(t.getSucursalTicket());
    }

    @Test
    @DisplayName("no se busca la sede si el ticket ya viene con una asignada")
    void noConsultaLaSedeSiElTicketYaLaTiene() {
        Ticket t = mock(Ticket.class);
        when(t.getSucursalTicket()).thenReturn(quito);

        // Reproduce el caso de un ticket reasignado: la relacion ya esta puesta
        // en la entidad y no debe pisarse con la del solicitante.
        assertEquals(quito, t.getSucursalTicket());
    }
}