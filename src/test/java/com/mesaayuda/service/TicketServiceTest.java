package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.mapper.AnswerMapper;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

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

    @InjectMocks private TicketService ticketService;

@BeforeEach
  void setUp() {
      ReflectionTestUtils.setField(ticketService, "uploadDir", "target/test-uploads");
      // Politica real, no un mock: estos tests comprueban la autorizacion y un
      // mock de TicketAccessPolicy devolveria siempre false, es decir, denegaria
      // todo y las pruebas pasarian sin comprobar nada.
      TicketAccessPolicy politica = new TicketAccessPolicy();
      ReflectionTestUtils.setField(politica, "userService", userService);
      ReflectionTestUtils.setField(ticketService, "accessPolicy", politica);
  }

    private User user(UUID id) {
        User u = new User();
        u.setIdUser(id);
        return u;
    }

    /**
     * Usuario con rol. Las comprobaciones de acceso de TicketService leen el rol
     * del solicitante, así que un usuario sin rol no sirve para probar nada
     * relacionado con permisos.
     */
    private User userConRol(UUID id, String rol) {
        User u = user(id);
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    private void comoAdmin() {
        when(userService.getCurrentUser()).thenReturn(userConRol(id(999), "ADMIN"));
    }

    @Test
    void myTickets_queriesOnlyCurrentRequester() {
        User current = user(id(10));
        when(userService.getCurrentUser()).thenReturn(current);

        Ticket t1 = mock(Ticket.class);
        Ticket t2 = mock(Ticket.class);
        when(ticketRepository.findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(id(10)))
                .thenReturn(List.of(t1, t2));

        var result = ticketService.myTickets();

        verify(ticketRepository).findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(id(10));
        verify(ticketMapper).toResponse(t1);
        verify(ticketMapper).toResponse(t2);
        assertEquals(2, result.size());
    }

    @Test
    void assignedTickets_queriesOnlyCurrentAgent() {
        User current = user(id(10));
        when(userService.getCurrentUser()).thenReturn(current);

        Ticket t = mock(Ticket.class);
        when(ticketRepository.findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(id(10)))
                .thenReturn(List.of(t));

        var result = ticketService.assignedTickets();

        verify(ticketRepository).findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(id(10));
        verify(ticketMapper).toResponse(t);
        assertEquals(1, result.size());
    }

    @Test
    void search_callsRepositoryWithSpecAndPageable() {
        comoAdmin();
        Ticket t = mock(Ticket.class);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        var page = ticketService.search(null, null, null, null, null, null, null, null, 0, 10);

        verify(ticketRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(ticketMapper).toResponse(t);
        assertEquals(1, page.getContent().size());
    }

    @Test
    void uploadAttachment_emptyFile_throws() {
        comoAdmin();
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(id(1))).thenReturn(java.util.Optional.of(ticket));

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        com.mesaayuda.exception.BadRequestException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.mesaayuda.exception.BadRequestException.class,
                () -> ticketService.uploadAttachment(id(1), null, file));
        assertEquals("El archivo está vacío", ex.getMessage());
        verify(attachRepository, never()).save(any());
    }

    /**
     * La cabecera de la ficha de detalle se alimenta de este endpoint y muestra el
     * punto de sesión, así que findById debe devolver el estado resuelto.
     */
    @Test
    void findById_devuelveElEstadoDeSesionDelSolicitante() {
        comoAdmin();
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(id(1))).thenReturn(java.util.Optional.of(ticket));
        when(ticketMapper.toResponse(ticket)).thenReturn(respuesta(id(1), id(77)));
        when(presenceService.estadoDe(id(77))).thenReturn(PresenceService.EN_LINEA);

        TicketResponse r = ticketService.findById(id(1));

        assertEquals(PresenceService.EN_LINEA, r.userSessionState());
    }

    /**
     * Un ticket sin solicitante no debe romper la carga de la ficha. El
     * PresenceService real responde DESCONECTADO ante un id nulo; aquí se
     * comprueba que se le consulta con null y que la respuesta se devuelve entera.
     */
    @Test
    void findById_ticketSinSolicitanteNoRompe() {
        comoAdmin();
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(id(1))).thenReturn(java.util.Optional.of(ticket));
        when(ticketMapper.toResponse(ticket)).thenReturn(respuesta(id(1), null));

        TicketResponse r = ticketService.findById(id(1));

        verify(presenceService).estadoDe(null);
        assertNotNull(r);
        assertNull(r.userId());
    }

    private TicketResponse respuesta(UUID id, UUID userId) {
        return new TicketResponse(
                id, userId, "titulo", "descripcion", "solicitante", "Solicitante",
                null, "ACTIVE", null, null, null, null, null, null, null, null, null,
                null, 0, null, null, null, null);
    }
}