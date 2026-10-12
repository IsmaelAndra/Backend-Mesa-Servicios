package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.MesaUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A quién se avisa que alguien está escribiendo.
 *
 * El fallo era que el aviso solo llegaba al titular y al agente asignado. Como
 * los administradores ven todos los tickets, un ADMIN que estaba mirando uno
 * recibía el evento cuando escribía el titular pero no al revés, y el punto de
 * "escribiendo" no le aparecía.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotifyTypingAudienceTest {

    private static final UUID TITULAR = id(4);
    private static final UUID AGENTE = id(3);
    private static final UUID ADMIN = id(2);
    private static final UUID SUPER_ADMIN = id(1);
    private static final UUID INACTIVO = id(9);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SseEventBroker sseEventBroker;
    @Mock
    private UserService userService;
    @Mock
    private com.mesaayuda.repository.TicketRepository ticketRepository;

    private TicketService ticketService;

@BeforeEach
  void setUp() {
      ticketService = new TicketService();
      set("userRepository", userRepository);
      set("sseEventBroker", sseEventBroker);
      set("userService", userService);
      set("ticketRepository", ticketRepository);
      // Se inyecta la politica real, no un mock: estos tests comprueban quien se
      // trata como personal, y un mock devolveria siempre false.
      TicketAccessPolicy politica = new TicketAccessPolicy();
      try {
          var campo = TicketAccessPolicy.class.getDeclaredField("userService");
          campo.setAccessible(true);
          campo.set(politica, userService);
      } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
      }
      set("accessPolicy", politica);
  }

    private void set(String name, Object value) {
        try {
            var campo = TicketService.class.getDeclaredField(name);
            campo.setAccessible(true);
            campo.set(ticketService, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private User usuario(UUID id, String email, String rol) {
        User u = new User();
        u.setIdUser(id);
        u.setCorporateEmailUser(email);
        u.setStatusUser("ACTIVE");
        u.setDeletedUser(false);
        // rolUser es una entidad Role, no un enum: el codigo compara por
        // getNameRole().
        com.mesaayuda.entity.Role r = new com.mesaayuda.entity.Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    /** Contexto de seguridad con el usuario que escribe. */
    private void autenticarComo(User u) {
        var principal = org.mockito.Mockito.mock(MesaUserDetails.class);
        org.mockito.Mockito.when(principal.getUsername()).thenReturn(u.getCorporateEmailUser());
        org.mockito.Mockito.when(userService.getCurrentUser()).thenReturn(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> audienciaPublicada(User escribe, Ticket ticket) {
        // getTicketLegible termina en getTicket, que solo usa findById.
        org.mockito.Mockito.when(ticketRepository.findById(eq(ticket.getIdTicket())))
                .thenReturn(java.util.Optional.of(ticket));

        ticketService.notifyTyping(ticket.getIdTicket());

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(sseEventBroker).publishTyping(eq(ticket.getIdTicket()),
                eq(escribe.getIdUser()), anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void avisaAlTitularAlAgenteYATodosLosAdministradores() {
        Ticket ticket = new Ticket();
        ticket.setIdTicket(id(1));
        User titular = usuario(TITULAR, "usuario@coacmagisterio.fin.ec", "USER");
        User agente = usuario(AGENTE, "agente@coacmagisterio.fin.ec", "AGENT");
        ticket.setUserTicket(titular);
        ticket.setAgentTicket(agente);

        User admin = usuario(ADMIN, "admin@coacmagisterio.fin.ec", "ADMIN");
        User superAdmin = usuario(SUPER_ADMIN, "superadmin@coacmagisterio.fin.ec", "SUPER_ADMIN");
        org.mockito.Mockito.when(userRepository.findByRoleName("ADMIN"))
                .thenReturn(List.of(admin));
        org.mockito.Mockito.when(userRepository.findByRoleName("SUPER_ADMIN"))
                .thenReturn(List.of(superAdmin));

        // Escribe el titular, que es el caso que antes no llegaba al admin.
        autenticarComo(titular);
        Collection<UUID> audiencia = audienciaPublicada(titular, ticket);

        assertTrue(audiencia.contains(AGENTE), "debe avisar al agente asignado");
        assertTrue(audiencia.contains(ADMIN), "debe avisar a los administradores");
        assertTrue(audiencia.contains(SUPER_ADMIN), "debe avisar al super administrador");
    }

    @Test
    void nuncaSeAvisaAQuienEscribe() {
        Ticket ticket = new Ticket();
        ticket.setIdTicket(id(1));
        User titular = usuario(TITULAR, "usuario@coacmagisterio.fin.ec", "USER");
        ticket.setUserTicket(titular);
        ticket.setAgentTicket(null);

        org.mockito.Mockito.when(userRepository.findByRoleName(anyString())).thenReturn(List.of());

        autenticarComo(titular);
        Collection<UUID> audiencia = audienciaPublicada(titular, ticket);

        // Para que no vea su propio nombre en el indicador.
        assertFalse(audiencia.contains(TITULAR), "no debe avisarse a si mismo");
    }

    @Test
    void avisaAlSuperAdminCuandoEscribeElTitular() {
        // El caso exacto que seBroken: titular escribe, el super admin mira el
        // ticket y no le aparece nada.
        Ticket ticket = new Ticket();
        ticket.setIdTicket(id(1));
        User titular = usuario(TITULAR, "usuario@coacmagisterio.fin.ec", "USER");
        ticket.setUserTicket(titular);
        ticket.setAgentTicket(usuario(AGENTE, "agente@coacmagisterio.fin.ec", "AGENT"));

        User superAdmin = usuario(SUPER_ADMIN, "superadmin@coacmagisterio.fin.ec", "SUPER_ADMIN");
        org.mockito.Mockito.when(userRepository.findByRoleName("ADMIN")).thenReturn(List.of());
        org.mockito.Mockito.when(userRepository.findByRoleName("SUPER_ADMIN"))
                .thenReturn(List.of(superAdmin));

        autenticarComo(titular);
        Collection<UUID> audiencia = audienciaPublicada(titular, ticket);

        assertTrue(audiencia.contains(SUPER_ADMIN),
                "el super admin debe recibir el aviso aunque no sea el agente asignado");
    }

    @Test
    void unAdminActivoSeIncluyeConElEstadoComoTexto() {
        // statusUser es un String. Con la comprobacion anterior
        // (Boolean.TRUE.equals sobre un String) "ACTIVE" nunca cuadraba y el
        // descarte se aplicaba siempre: ningun administrador recibia nada.
        Ticket ticket = new Ticket();
        ticket.setIdTicket(id(1));
        User titular = usuario(TITULAR, "usuario@coacmagisterio.fin.ec", "USER");
        ticket.setUserTicket(titular);

        User admin = usuario(ADMIN, "admin@coacmagisterio.fin.ec", "ADMIN");
        // Recordatorio de que statusUser es texto: si algun dia se cambia de
        // tipo, esta asercion avisa.
        assertTrue("ACTIVE".equals(admin.getStatusUser()),
                "el usuario de prueba debe llevar el estado como texto");

        org.mockito.Mockito.when(userRepository.findByRoleName("ADMIN"))
                .thenReturn(List.of(admin));
        org.mockito.Mockito.when(userRepository.findByRoleName("SUPER_ADMIN")).thenReturn(List.of());

        autenticarComo(titular);
        Collection<UUID> audiencia = audienciaPublicada(titular, ticket);

        assertTrue(audiencia.contains(ADMIN),
                "un admin ACTIVE con el estado como texto debe recibir el aviso");
    }

    @Test
    void ignoraAdministradoresBorradosOInactivos() {
        Ticket ticket = new Ticket();
        ticket.setIdTicket(id(1));
        User titular = usuario(TITULAR, "usuario@coacmagisterio.fin.ec", "USER");
        ticket.setUserTicket(titular);

        User borrado = usuario(id(8), "borrado@coacmagisterio.fin.ec", "ADMIN");
        borrado.setDeletedUser(true);
        User inactivo = usuario(INACTIVO, "inactivo@coacmagisterio.fin.ec", "ADMIN");
        inactivo.setStatusUser("INACTIVE");

        org.mockito.Mockito.when(userRepository.findByRoleName("ADMIN"))
                .thenReturn(List.of(borrado, inactivo));
        org.mockito.Mockito.when(userRepository.findByRoleName("SUPER_ADMIN")).thenReturn(List.of());

        autenticarComo(titular);
        Collection<UUID> audiencia = audienciaPublicada(titular, ticket);

        assertFalse(audiencia.contains(id(8)), "un admin borrado no debe recibir avisos");
        assertFalse(audiencia.contains(INACTIVO), "un admin inactivo no debe recibir avisos");
    }
}