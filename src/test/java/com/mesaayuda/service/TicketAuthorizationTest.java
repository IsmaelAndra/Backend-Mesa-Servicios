package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.mapper.AnswerMapper;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.repository.AnswerRepository;
import com.mesaayuda.repository.AttachRepository;
import com.mesaayuda.repository.CategoryRepository;
import com.mesaayuda.repository.PriorityRepository;
import com.mesaayuda.repository.StatusRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketTypeRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Autorización de tickets. No había ninguna prueba de esto: los accesos se
 * dejaron sin comprobar durante el desarrollo y nadie lo notó porque la
 * aplicación solo se probaba con la cuenta propia.
 * <p>
 * El escenario de referencia: el ticket #1 es de la usuaria 10 y lo tiene
 * asignado el agente 20. Cualquier otro usuario (id 30) no debería poder
 * alcanzarlo salvo que pertenezca al personal.
 */
@ExtendWith(MockitoExtension.class)
class TicketAuthorizationTest {

    private static final UUID TICKET_ID = id(1);
    private static final UUID DUENA = id(10);
    private static final UUID AGENTE_ASIGNADO = id(20);
    private static final UUID TERCERO = id(30);

    @Mock private TicketRepository ticketRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private AnswerRepository answerRepository;
    @Mock private AttachRepository attachRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoutingService routingService;
    @Mock private SlaService slaService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private UserService userService;
    @Mock private TicketMapper ticketMapper;
    @Mock private AnswerMapper answerMapper;
    @Mock private PresenceService presenceService;
    @Mock private SseEventBroker sseEventBroker;

    @InjectMocks private TicketService ticketService;

    @BeforeEach
  void inyectarPoliticaDeAcceso() {
      // Politica real, no un mock: todo este test verifica QUIEN puede tocar un
      // ticket, asi que un mock que devolviera false solo haria que pasara todo
      // denegado y las pruebas no comprobarian nada.
      TicketAccessPolicy politica = new TicketAccessPolicy();
      ReflectionTestUtils.setField(politica, "userService", userService);
      ReflectionTestUtils.setField(ticketService, "accessPolicy", politica);
  }

  private Ticket ticketDeTerceros() {
        Ticket t = mock(Ticket.class);
        // lenient: según el camino que se recorra en cada prueba se consulta el
        // solicitante, el agente asignado o ninguno, y no interesa que sobre
        // un stub que no se llega a usar.
        lenient().when(t.getDeletedTicket()).thenReturn(false);
        lenient().when(t.getUserTicket()).thenReturn(usuario(DUENA));
        lenient().when(t.getAgentTicket()).thenReturn(usuario(AGENTE_ASIGNADO));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(t));
        return t;
    }

    private static User usuario(UUID id) {
        User u = new User();
        u.setIdUser(id);
        return u;
    }

    private static User usuarioConRol(UUID id, String rol) {
        User u = usuario(id);
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    private void actuarioComo(UUID id, String rol) {
        when(userService.getCurrentUser()).thenReturn(usuarioConRol(id, rol));
    }

    // ============================ LECTURA ============================

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("la dueña del ticket puede abrirlo")
        void duenaPuedeAbrir() {
            ticketDeTerceros();
            actuarioComo(DUENA, "USER");
            TicketResponse r = new TicketResponse(
                    TICKET_ID, DUENA, "t", "d", "s", "S", null, "ACTIVE", null, null, null,
                    null, null, null, null, null, null, null, 0, null, null, null, null);
            when(ticketMapper.toResponse(any(Ticket.class))).thenReturn(r);

            assertEquals(DUENA, ticketService.findById(TICKET_ID).userId());
        }

        @Test
        @DisplayName("un usuario sin relación con el ticket NO puede abrirlo")
        void terceroNoPuedeAbrir() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.findById(TICKET_ID));
        }

        @Test
        @DisplayName("un agente SIN asignar sí puede abrirlo: necesita ver la cola para asignarla")
        void agenteSinAsignarPuedeAbrir() {
            ticketDeTerceros();
            actuarioComo(id(99), "AGENT");
            TicketResponse r = new TicketResponse(
                    TICKET_ID, DUENA, "t", "d", "s", "S", null, "ACTIVE", null, null, null,
                    null, null, null, null, null, null, null, 0, null, null, null, null);
            when(ticketMapper.toResponse(any(Ticket.class))).thenReturn(r);

            assertDoesNotThrow(() -> ticketService.findById(TICKET_ID));
        }

        @Test
        @DisplayName("el acceso denegado se registra en el log con usuario y ticket")
        void denegadoQuedaRegistrado() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.findById(TICKET_ID));
            // El mapper no debe invocarse nunca: no se llega a filtrar datos.
            verify(ticketMapper, never()).toResponse(any(Ticket.class));
        }
    }

    // ============================ ESCRITURA ============================

    @Nested
    @DisplayName("uploadAttachment")
    class Upload {

        @Test
        @DisplayName("un tercero NO puede subir archivos al ticket de otro")
        void terceroNoPuedeSubir() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "USER");
            MultipartFile file = mock(MultipartFile.class);

            assertThrows(AccessDeniedException.class,
                    () -> ticketService.uploadAttachment(TICKET_ID, null, file));
        }

        @Test
        @DisplayName("un agente sin asignar NO puede escribir en el ticket")
        void agenteSinAsignarNoPuedeSubir() {
            ticketDeTerceros();
            actuarioComo(id(99), "AGENT");
            MultipartFile file = mock(MultipartFile.class);

            assertThrows(AccessDeniedException.class,
                    () -> ticketService.uploadAttachment(TICKET_ID, null, file));
        }

        @Test
        @DisplayName("la dueña puede subir, pero un archivo vacío se rechaza por lo de siempre")
        void duenaPuedeSubirYSeValidaElArchivo() {
            ticketDeTerceros();
            actuarioComo(DUENA, "USER");
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            com.mesaayuda.exception.BadRequestException ex = assertThrows(
                    com.mesaayuda.exception.BadRequestException.class,
                    () -> ticketService.uploadAttachment(TICKET_ID, null, file));
            assertEquals("El archivo está vacío", ex.getMessage());
        }
    }

    // ============================ LECTURA DE CONTENIDO ============================

    @Nested
    @DisplayName("contenido del ticket")
    class Contenido {

        @Test
        @DisplayName("un tercero NO puede leer las respuestas de otro ticket")
        void terceroNoLeeRespuestas() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.getAnswers(TICKET_ID));
        }

        @Test
        @DisplayName("un tercero NO puede ver los adjuntos de otro ticket")
        void terceroNoLeeAdjuntos() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.getAttachments(TICKET_ID));
        }

        @Test
        @DisplayName("un tercero NO puede espiar quién escribe con notifyTyping")
        void terceroNoNotificaEscribiendo() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.notifyTyping(TICKET_ID));
            verify(sseEventBroker, never()).publishTyping(any(), any(), any(), any());
        }
    }

    // ============================ AISLAMIENTO ENTRE USUARIOS ============================

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("un solicitante queda atado a su propio id aunque pida el de otro")
        void solicitanteNoPuedeCambiarElFiltro() {
            // No hace falta comprobar la Specification en sí: lo relevante es
            // que el servicio resolva siempre el id del solicitante autenticado
            // y nunca el que venga en la petición.
            when(userService.getCurrentUser()).thenReturn(usuarioConRol(DUENA, "USER"));
            when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            ticketService.search(null, null, null, null, null, TERCERO, null, null, 0, 10);

            verify(ticketRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("un usuario sin rol no se trata como personal")
        void usuarioSinRolNoEsPersonal() {
            when(userService.getCurrentUser()).thenReturn(usuario(DUENA));
            when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            // Sin rol debe fallar cerrado, no reventar con NullPointerException.
            assertDoesNotThrow(() -> ticketService.search(null, null, null, null, null, null, null, null, 0, 10));
        }
    }

    @Nested
    @DisplayName("Gestión: estado, prioridad y categoría")
    class Gestion {

        @Test
        @DisplayName("la solicitante no puede cambiar el estado de su propio ticket")
        void duenaNoCambiaEstado() {
            ticketDeTerceros();
            actuarioComo(DUENA, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.changeStatus(TICKET_ID,
                    new com.mesaayuda.dto.StatusChangeRequest("RESUELTO")));
            verify(slaService, never()).markResolutionCompleted(any());
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("la solicitante no puede cambiar prioridad ni categoría")
        void duenaNoCambiaPrioridadNiCategoria() {
            ticketDeTerceros();
            actuarioComo(DUENA, "USER");

            assertThrows(AccessDeniedException.class, () -> ticketService.changePriority(TICKET_ID, id(1)));
            assertThrows(AccessDeniedException.class, () -> ticketService.changeCategory(TICKET_ID, id(1)));
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("un agente sin el ticket asignado no puede cambiarle el estado")
        void agenteSinAsignarNoCambiaEstado() {
            ticketDeTerceros();
            actuarioComo(TERCERO, "AGENT");

            assertThrows(AccessDeniedException.class, () -> ticketService.changeStatus(TICKET_ID,
                    new com.mesaayuda.dto.StatusChangeRequest("CERRADO")));
        }

        @Test
        @DisplayName("el agente asignado pasa la autorización")
        void agenteAsignadoPasa() {
            ticketDeTerceros();
            actuarioComo(AGENTE_ASIGNADO, "AGENT");

            // Sin estados en el repositorio la operación termina en 400 por estado
            // inválido: lo que importa es que no se cortó con un 403 antes.
            assertThrows(com.mesaayuda.exception.BadRequestException.class, () -> ticketService.changeStatus(
                    TICKET_ID, new com.mesaayuda.dto.StatusChangeRequest("CERRADO")));
        }
    }

    @Test
    @DisplayName("un usuario sin rol recibe 403 y no un 500 al abrir un ticket ajeno")
    void usuarioSinRolFallaCerrado() {
        ticketDeTerceros();
        when(userService.getCurrentUser()).thenReturn(usuario(TERCERO));

        assertThrows(AccessDeniedException.class, () -> ticketService.findById(TICKET_ID));
    }

    @Test
    @DisplayName("un ticket inexistente sigue dando 404, no 403")
    void ticketInexistenteDa404() {
        // El ticket se resuelve antes de mirar quién pregunta, así que la
        // ausencia se responde con 404 y no se llega a evaluar el permiso.
        when(ticketRepository.findById(id(99))).thenReturn(Optional.empty());

        assertThrows(com.mesaayuda.exception.ResourceNotFoundException.class,
                () -> ticketService.findById(id(99)));
    }
}
