package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.AnswerPageResponse;
import com.mesaayuda.dto.AnswerResponse;
import com.mesaayuda.entity.AnswerRead;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Answer;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.AnswerReadRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El chat ya no pagina en el navegador: el servidor entrega las páginas de más
 * reciente a más antigua. Estas pruebas fijan que el orden, el filtrado de
 * notas internas y la marca de leídos siguen siendo correctos en ese modelo.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceAnswerPageTest {

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
    @Mock private com.mesaayuda.mapper.TicketMapper ticketMapper;
    @Mock private com.mesaayuda.mapper.AnswerMapper answerMapper;
    @Mock private PresenceService presenceService;
    @Mock private SseEventBroker sseEventBroker;
    @Mock private AnswerReadRepository answerReadRepository;

    /**
     * La visibilidad de los acuses la decide el administrador, asi que por
     * defecto estos tests se centran en la pagina y no en el filtro: se deja
     * como "todo el mundo lo ve" salvo que una prueba diga lo contrario.
     * <p>
     * Se marca como opcional porque hay pruebas (pagina vacia, acceso denegado)
     * que nunca llegan a construir una respuesta, y ahi el stub sobraria y
     * Mockito lo cobraria como error.
     */
    @Mock private ChatVisibilityPolicy chatVisibilityPolicy;

    @InjectMocks private TicketService ticketService;

@BeforeEach
  void permisosPorDefecto() {
      org.mockito.Mockito.lenient()
              .when(chatVisibilityPolicy.puedeVer(any()))
              .thenReturn(true);
      org.mockito.Mockito.lenient()
              .when(chatVisibilityPolicy.muestraNombres())
              .thenReturn(true);
      // Politica real: hay pruebas de que un tercero no lee el hilo, y con un
      // mock que devolviera false se negaria todo y pasarian sin comprobar nada.
      TicketAccessPolicy politica = new TicketAccessPolicy();
      ReflectionTestUtils.setField(politica, "userService", userService);
      ReflectionTestUtils.setField(ticketService, "accessPolicy", politica);
  }

    private static final UUID TICKET = id(1);
    private static final UUID DUENA = id(10);
    private static final UUID AGENTE = id(20);

    private static User usuario(UUID id, String rol) {
        User u = new User();
        u.setIdUser(id);
        u.setCorporateEmailUser("u" + id + "@coacmagisterio.fin.ec");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    private static Answer respuesta(UUID id, UUID autor, boolean interna) {
        Ticket t = new Ticket();
        t.setIdTicket(TICKET);
        t.setUserTicket(usuario(DUENA, "USER"));
        t.setAgentTicket(usuario(AGENTE, "AGENT"));
        Status st = new Status();
        st.setNameStatus("EN_PROCESO");
        t.setStatusTicket(st);
        Answer a = new Answer();
        a.setIdAnswer(id);
        a.setMessageAnswer("mensaje " + id);
        a.setInternalAnswer(interna);
        a.setUserAnswer(usuario(autor, "AGENT"));
        a.setTicketAnswer(t);
        return a;
    }

    private void comoSolicitante() {
        when(userService.getCurrentUser()).thenReturn(usuario(DUENA, "USER"));
        Ticket t = new Ticket();
        t.setIdTicket(TICKET);
        t.setUserTicket(usuario(DUENA, "USER"));
        t.setAgentTicket(usuario(AGENTE, "AGENT"));
        t.setDeletedTicket(false);
        when(ticketRepository.findById(TICKET)).thenReturn(Optional.of(t));
    }

    private void comoAgenteAsignado() {
        when(userService.getCurrentUser()).thenReturn(usuario(AGENTE, "AGENT"));
        Ticket t = new Ticket();
        t.setIdTicket(TICKET);
        t.setUserTicket(usuario(DUENA, "USER"));
        t.setAgentTicket(usuario(AGENTE, "AGENT"));
        t.setDeletedTicket(false);
        when(ticketRepository.findById(TICKET)).thenReturn(Optional.of(t));
    }

    @Test
    @DisplayName("la página llega del más antiguo al más reciente aunque la consulta venga al revés")
    void invierteElOrdenDeLaConsulta() {
        comoSolicitante();
        // La consulta pide los más recientes primero: 30, 20, 10.
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(30), AGENTE, false),
                        respuesta(id(20), DUENA, false),
                        respuesta(id(10), AGENTE, false)));
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(3L);
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of());

        AnswerPageResponse pagina = ticketService.getAnswersPage(TICKET, 0, 20);

        assertThat(pagina.answers()).extracting(a -> a.idAnswer())
                .containsExactly(id(10), id(20), id(30));
    }

    @Test
    @DisplayName("indica si hay mensajes más antiguos que cargar")
    void calculaSiHayMas() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(21), AGENTE, false), respuesta(id(20), AGENTE, false)));
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(25L);
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of());

        assertThat(ticketService.getAnswersPage(TICKET, 0, 2).hasMore()).isTrue();
        assertThat(ticketService.getAnswersPage(TICKET, 12, 2).hasMore()).isFalse();
    }

    @Test
    @DisplayName("el solicitante no recibe las notas internas ni en la cuenta")
    void ocultaNotasInternasAlSolicitante() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(2), AGENTE, false)));
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(1L);
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of());

        AnswerPageResponse pagina = ticketService.getAnswersPage(TICKET, 0, 20);

        assertThat(pagina.answers()).hasSize(1);
        assertThat(pagina.total()).isEqualTo(1L);
        // Se usa la consulta que filtra en el servidor, no la que trae todo.
        verify(answerRepository, never())
                .findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(any(java.util.UUID.class), any());
    }

    @Test
    @DisplayName("el agente asignado sí recibe las notas internas")
    void agenteAsignadoVeNotasInternas() {
        comoAgenteAsignado();
        when(answerRepository
                .findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(1), AGENTE, true)));
        when(answerRepository.countByTicketAnswerIdTicketAndDeletedAnswerFalse(TICKET)).thenReturn(1L);
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of());

        AnswerPageResponse pagina = ticketService.getAnswersPage(TICKET, 0, 20);

        assertThat(pagina.answers()).hasSize(1);
        assertThat(pagina.answers().get(0).internalAnswer()).isTrue();
    }

    @Test
    @DisplayName("un tercero sin relación no puede leer el hilo")
    void terceroNoLeeElHilo() {
        User otro = usuario(id(99), "USER");
        when(userService.getCurrentUser()).thenReturn(otro);
        Ticket t = new Ticket();
        t.setIdTicket(TICKET);
        t.setUserTicket(usuario(DUENA, "USER"));
        t.setAgentTicket(usuario(AGENTE, "AGENT"));
        t.setDeletedTicket(false);
        when(ticketRepository.findById(TICKET)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.getAnswersPage(TICKET, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(answerRepository, never())
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any());
    }

    @Test
    @DisplayName("una página vacía no rompe nada")
    void paginaVacia() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(0L);

        AnswerPageResponse pagina = ticketService.getAnswersPage(TICKET, 0, 20);

        assertThat(pagina.answers()).isEmpty();
        assertThat(pagina.hasMore()).isFalse();
        assertThat(pagina.total()).isZero();
    }

    @Test
    @DisplayName("si el usuario no puede ver los acuses, el backend no se los manda")
    void ocultaLosAcusesCuandoNoTienePermiso() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(9), AGENTE, false)));
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(1L);
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of());

        // El administrador ha restringido la visibilidad y este usuario no entra.
        when(chatVisibilityPolicy.puedeVer(any())).thenReturn(false);

        AnswerPageResponse pagina = ticketService.getAnswersPage(TICKET, 0, 20);

        // Lo importante es que el recuento y los nombres NO viajan en la
        // respuesta: si viajaran, se verían con las herramientas del navegador.
        assertThat(pagina.answers().get(0).readCount()).isNull();
        assertThat(pagina.answers().get(0).readBy()).isEmpty();
        // readByMe si viaja: es un hecho propio del solicitante, no informacion
        // sobre terceros. Sin el, el chat no podria distinguir un mensaje entrante
        // sin abrir de uno ya leido.
        assertThat(pagina.answers().get(0).readByMe()).isFalse();
    }

    @Test
    @DisplayName("sin permiso para ver acuses sigue sabiendo si el ya leyo el mensaje")
    void conservaReadByMeCuandoNoPuedeVerLosAcuses() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(9), AGENTE, false)));
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(1L);

        // El solicitante acaba de abrir el hilo: hay marca suya, y ademas la del
        // super admin, que es la que no debe viajarle.
        AnswerRead suya = new AnswerRead();
        suya.setAnswer(respuesta(id(9), AGENTE, false));
        suya.setUser(usuario(DUENA, "USER"));
        AnswerRead ajena = new AnswerRead();
        ajena.setAnswer(respuesta(id(9), AGENTE, false));
        ajena.setUser(usuario(AGENTE, "AGENT"));
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of(suya, ajena));

        when(chatVisibilityPolicy.puedeVer(any())).thenReturn(false);

        AnswerResponse a = ticketService.getAnswersPage(TICKET, 0, 20).answers().get(0);

        // El recuento de dos lecturas delata el trabajo del super admin y se oculta.
        assertThat(a.readCount()).isNull();
        assertThat(a.readBy()).isEmpty();
        // Pero "yo ya lo lei" si se conserva: sin esto, el frontend no puede
        // distinguir un mensaje entrante sin abrir de uno ya leido y marcaria
        // todos los mensajes del personal como "sin leer" para siempre.
        assertThat(a.readByMe()).isTrue();
    }

    @Test
    @DisplayName("con los nombres desactivados se ve el recuento pero no quien leyo")
    void recuentoSinNombres() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of(respuesta(id(9), AGENTE, false)));
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(1L);
        AnswerRead r = new AnswerRead();
        r.setAnswer(respuesta(id(9), AGENTE, false));
        r.setUser(usuario(AGENTE, "AGENT"));
        when(answerReadRepository.findByAnswerIdAnswerIn(any())).thenReturn(List.of(r));

        when(chatVisibilityPolicy.muestraNombres()).thenReturn(false);

        AnswerResponse a = ticketService.getAnswersPage(TICKET, 0, 20).answers().get(0);

        assertThat(a.readCount()).isEqualTo(1);
        assertThat(a.readBy()).isEmpty();
    }

    @Test
    @DisplayName("los parámetros rareos se corrigen en vez de romper la consulta")
    void corrigeParametrosRaros() {
        comoSolicitante();
        when(answerRepository
                .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                        any(java.util.UUID.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(answerRepository
                .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(TICKET))
                .thenReturn(0L);

        // Página negativa y tamaño enorme.
        AnswerPageResponse pagina = ticketService.getAnswersPage(TICKET, -5, 100_000);

        assertThat(pagina.page()).isZero();
        assertThat(pagina.size()).isEqualTo(100);
    }
}
