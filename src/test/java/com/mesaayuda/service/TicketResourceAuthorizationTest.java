package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketAudit;
import com.mesaayuda.entity.TicketRating;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.TicketAuditRepository;
import com.mesaayuda.repository.TicketRatingRepository;
import com.mesaayuda.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Autorización por recurso sobre la calificación y la auditoría de un ticket.
 *
 * Estos dos servicios no son TicketService: cada uno consulta el ticket por su
 * cuenta y por eso es facil que se les olvide la comprobación de acceso. Ya pasó.
 * Con solo tener sesión iniciada bastaba para pedir la calificación o el
 * historial de un ticket ajeno escribiendo su id en la URL, y no había ninguna
 * prueba que lo detectara porque todas las de autorización vivían en
 * TicketAuthorizationTest, que nunca tocó estas rutas.
 *
 * Los casos negativos son el motivo de existir esta clase: si alguien quita la
 * comprobación, falla aquí y no en produccion.
 */
@ExtendWith(MockitoExtension.class)
class TicketResourceAuthorizationTest {

    private static final UUID TICKET = id(1);
    private static final UUID DUENA = id(10);
    private static final UUID AGENTE = id(20);
    private static final UUID TERCERO = id(30);

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketRatingRepository ratingRepository;
    @Mock private TicketAuditRepository auditRepository;
    @Mock private UserService userService;

    @InjectMocks private RatingService ratingService;
    @InjectMocks private AuditService auditService;

    @BeforeEach
    void setUp() {
        TicketAccessPolicy politica = new TicketAccessPolicy();
        ReflectionTestUtils.setField(politica, "userService", userService);
        ReflectionTestUtils.setField(ratingService, "accessPolicy", politica);
        ReflectionTestUtils.setField(auditService, "accessPolicy", politica);
    }

    private static User usuario(UUID id, String rol) {
        User u = new User();
        u.setIdUser(id);
        u.setCorporateEmailUser("u" + id + "@coacmagisterio.fin.ec");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    /** Ticket de la persona DUENA, asignado a AGENTE y resuelto. */
    private Ticket ticketAjeno() {
        Area area = new Area();
        area.setIdArea(id(1));
        Ticket t = new Ticket();
        t.setIdTicket(TICKET);
        t.setDeletedTicket(false);
        t.setUserTicket(usuario(DUENA, "USER"));
        t.setAgentTicket(usuario(AGENTE, "AGENT"));
        t.setAreaTicket(area);
        return t;
    }

private void como(User u) {
        // lenient porque en las pruebas de 404 la peticion se corta al buscar el
        // ticket, antes de mirar quien pregunta: ahi el stub no se usa.
        lenient().when(userService.getCurrentUser()).thenReturn(u);
    }

    private void hayTicket() {
        when(ticketRepository.findById(TICKET)).thenReturn(Optional.of(ticketAjeno()));
    }

    // ============================ CALIFICACIÓN ============================

    /** Calificación de ejemplo, para las pruebas de quien SÍ tiene permiso. */
    private TicketRating calificacion() {
        TicketRating r = new TicketRating();
        r.setIdRating(id(5));
        r.setTicket(ticketAjeno());
        r.setOverallSatisfaction(5);
        return r;
    }

    @Test
    @DisplayName("un tercero NO puede leer la calificación de un ticket ajeno")
    void terceroNoLeeLaCalificacion() {
        hayTicket();
        como(usuario(TERCERO, "USER"));

        assertThrows(AccessDeniedException.class, () -> ratingService.getByTicket(TICKET));

        // Ni siquiera se llega a consultar la calificación.
        verify(ratingRepository, never()).findByTicketIdTicket(TICKET);
    }

    @Test
    @DisplayName("el titular sí puede leer su calificación")
    void titularLeeSuCalificacion() {
        hayTicket();
        como(usuario(DUENA, "USER"));
        when(ratingRepository.findByTicketIdTicket(TICKET)).thenReturn(Optional.of(calificacion()));

        assertDoesNotThrow(() -> ratingService.getByTicket(TICKET));
    }

    @Test
    @DisplayName("un agente puede leer la calificación de un ticket que tiene asignado")
    void agenteAsignadoLeeLaCalificacion() {
        hayTicket();
        como(usuario(AGENTE, "AGENT"));
        when(ratingRepository.findByTicketIdTicket(TICKET)).thenReturn(Optional.of(calificacion()));

        assertDoesNotThrow(() -> ratingService.getByTicket(TICKET));
    }

    @Test
    @DisplayName("un administrador puede leer la calificación")
    void administradorLeeLaCalificacion() {
        hayTicket();
        como(usuario(id(99), "ADMIN"));
        when(ratingRepository.findByTicketIdTicket(TICKET)).thenReturn(Optional.of(calificacion()));

        assertDoesNotThrow(() -> ratingService.getByTicket(TICKET));
    }

    @Test
    @DisplayName("con permiso pero sin calificación sale 404, no un error raro")
    void sinCalificacionResponde404() {
        // El permiso se concede y despues se avisa de que no hay nada: es
        // distinto de negarlo, y el orden importa para no filtrar existencia.
        hayTicket();
        como(usuario(id(99), "ADMIN"));
        when(ratingRepository.findByTicketIdTicket(TICKET)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ratingService.getByTicket(TICKET));
    }

    @Test
    @DisplayName("el acceso denegado a la calificación no llega a tocar los datos")
    void accesoDenegadoNoConsultaNada() {
        // El log del intento lo escribe la propia politica; lo que importa aqui es
        // que la fila de la calificación no se lee si el permiso no se concede.
        hayTicket();
        como(usuario(TERCERO, "USER"));

        assertThrows(AccessDeniedException.class, () -> ratingService.getByTicket(TICKET));
        verify(ratingRepository, never()).findByTicketIdTicket(TICKET);
    }

    // ============================ AUDITORÍA ============================

    @Test
    @DisplayName("un tercero NO puede leer la auditoría de un ticket ajeno")
    void terceroNoLeeLaAuditoria() {
        hayTicket();
        como(usuario(TERCERO, "USER"));

        assertThrows(AccessDeniedException.class, () -> auditService.findByTicket(TICKET));

        verify(auditRepository, never()).findByTicketIdTicketOrderByCreatedAtAuditDesc(TICKET);
    }

    @Test
    @DisplayName("el titular sí puede leer la auditoría de su ticket")
    void titularLeeSuAuditoria() {
        hayTicket();
        como(usuario(DUENA, "USER"));
        TicketAudit a = new TicketAudit();
        a.setIdAudit(id(1));
        a.setActionType("STATUS");
        when(auditRepository.findByTicketIdTicketOrderByCreatedAtAuditDesc(TICKET)).thenReturn(List.of(a));

        assertDoesNotThrow(() -> auditService.findByTicket(TICKET));
    }

    @Test
    @DisplayName("cualquier agente del personal ve el historial, aunque no lleve el ticket")
    void agenteDelPersonalLeeLaAuditoria() {
        // Deliberado: el personal ve la cola completa porque sin eso el kanban no
        // funciona, tiene que poder abrir un ticket sin asignar. Lo que se
        // restringe es el rol USER, no el personal.
        hayTicket();
        como(usuario(id(77), "AGENT"));
        when(auditRepository.findByTicketIdTicketOrderByCreatedAtAuditDesc(TICKET)).thenReturn(List.of());

        assertDoesNotThrow(() -> auditService.findByTicket(TICKET));
    }

    @Test
    @DisplayName("un usuario sin rol falla cerrado, no se le abre el historial")
    void usuarioSinRolNoLeeLaAuditoria() {
        // Si el rol llega a null, el permiso se niega. Fallar hacia abierto seria
        // un agujero, y reventar con una excepcion daria un 500 en vez de un 403.
        hayTicket();
        User sinRol = new User();
        sinRol.setIdUser(id(55));
        sinRol.setCorporateEmailUser("sin-rol@coacmagisterio.fin.ec");
        como(sinRol);

        assertThrows(AccessDeniedException.class, () -> auditService.findByTicket(TICKET));
    }

    @Test
    @DisplayName("un ticket eliminado responde 404, no expone su historial")
    void ticketEliminadoNoExponeAuditoria() {
        Ticket borrado = ticketAjeno();
        borrado.setDeletedTicket(true);
        when(ticketRepository.findById(TICKET)).thenReturn(Optional.of(borrado));
        como(usuario(id(99), "ADMIN"));

        assertThrows(ResourceNotFoundException.class, () -> auditService.findByTicket(TICKET));
        verify(auditRepository, never()).findByTicketIdTicketOrderByCreatedAtAuditDesc(TICKET);
    }

@Test
    @DisplayName("un ticket inexistente da 404 y no toca los datos de la calificación")
    void ticketInexistenteNoConsultaLaCalificacion() {
        // Nota honesta sobre lo que esto NO cubre: se responde 404 antes de
        // comprobar el permiso, asi que un usuario con sesion puede distinguir
        // "ese id no existe" de "ese id existe pero no es suyo". Es una fuga
        // pequena (cuantos tickets hay y como se reparten los ids), y la
        // alternativa, responder 403 tambien cuando no existe, obliga al
        // frontend a diferenciar dos casos que en la practica son el mismo.
        when(ticketRepository.findById(id(999))).thenReturn(Optional.empty());
        como(usuario(TERCERO, "USER"));

        assertThrows(ResourceNotFoundException.class, () -> ratingService.getByTicket(id(999)));
verify(ratingRepository, never()).findByTicketIdTicket(id(999));
    }
}