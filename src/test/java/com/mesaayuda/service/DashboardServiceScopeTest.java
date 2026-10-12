package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.DashboardStats;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un agente solo debe ver sus propias estadisticas. Ademas el total y los
 * graficos tienen que salir de la misma poblacion: antes el total contaba
 * tambien los tickets CERRADO y los graficos no, de modo que las cifras de un
 * agente no cuadraban entre si.
 */
class DashboardServiceScopeTest {

    private static final UUID AGENTE_ID = id(3);

    private DashboardService service;
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        service = new DashboardService();
        ticketRepository = Mockito.mock(TicketRepository.class);
        ReflectionTestUtils.setField(service, "ticketRepository", ticketRepository);
        ReflectionTestUtils.setField(service, "ticketSlaRepository",
                Mockito.mock(TicketSlaRepository.class));
        ReflectionTestUtils.setField(service, "ticketRatingRepository",
                Mockito.mock(com.mesaayuda.repository.TicketRatingRepository.class));
        ReflectionTestUtils.setField(service, "userService", Mockito.mock(UserService.class));
    }

    @Test
    void elAgenteConsultaSusPropiosTicketsYNoLosDeLosDemas() {
        usuarioActual("AGENT", AGENTE_ID);
        when(ticketRepository.findTicketsBetweenByAgent(any(), any(), any(java.util.UUID.class)))
                .thenReturn(List.of(ticket(id(1), "ASIGNADO", "TI"), ticket(id(2), "CERRADO", "TI")));

        DashboardStats stats = service.getStats(null, null, null, null, null);

        verify(ticketRepository).findTicketsBetweenByAgent(any(), any(), eq(AGENTE_ID));
        verify(ticketRepository, never()).findTicketsBetween(any(), any());
        verify(ticketRepository, never()).findTicketsBetweenByUser(any(), any(), any(java.util.UUID.class));
        verify(ticketRepository, never()).findOpenTickets();
        verify(ticketRepository, never()).findOpenTicketsByAgent(any(java.util.UUID.class));

        assertEquals(2L, stats.totalTickets());
        // El CERRADO cuenta en el total pero no como ticket abierto.
        assertEquals(1L, stats.openTickets());
    }

    @Test
    void elTotalDelAgenteCoincideConLaSumaDeSusGraficos() {
        usuarioActual("AGENT", AGENTE_ID);
        when(ticketRepository.findTicketsBetweenByAgent(any(), any(), any(java.util.UUID.class)))
                .thenReturn(List.of(
                        ticket(id(1), "ASIGNADO", "TI"),
                        ticket(id(2), "CERRADO", "TI"),
                        ticket(id(3), "RESUELTO", "Operaciones")));

        DashboardStats stats = service.getStats(null, null, null, null, null);

        assertEquals(3L, stats.totalTickets());
        assertEquals(3L, stats.ticketsByStatus().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(3L, stats.ticketsByArea().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(3L, stats.ticketsByCategory().values().stream().mapToLong(Long::longValue).sum());
        assertTrue(stats.ticketsByStatus().containsKey("CERRADO"),
                "el grafico de estados debe incluir los CERRADO del agente");
    }

    @Test
    void elAdministradorSigueViendoTodosLosTickets() {
        usuarioActual("ADMIN", id(2));
        when(ticketRepository.findTicketsBetween(any(), any()))
                .thenReturn(List.of(ticket(id(1), "ASIGNADO", "TI"), ticket(id(2), "CERRADO", "TI")));

        DashboardStats stats = service.getStats(null, null, null, null, null);

        verify(ticketRepository).findTicketsBetween(any(), any());
        verify(ticketRepository, never()).findTicketsBetweenByAgent(any(), any(), any(java.util.UUID.class));
        assertEquals(2L, stats.totalTickets());
    }

    @Test
    void elSolicitanteSoloVeLosTicketsQueCreo() {
        usuarioActual("USER", id(4));
        when(ticketRepository.findTicketsBetweenByUser(any(), any(), any(java.util.UUID.class)))
                .thenReturn(List.of(ticket(id(9), "ABIERTO", "Operaciones")));

        DashboardStats stats = service.getStats(null, null, null, null, null);

        verify(ticketRepository).findTicketsBetweenByUser(any(), any(), eq(id(4)));
        assertEquals(1L, stats.totalTickets());
    }

    private void usuarioActual(String rol, UUID id) {
        UserService userService = Mockito.mock(UserService.class);
        User user = new User();
        user.setIdUser(id);
        Role r = new Role();
        r.setNameRole(rol);
        user.setRolUser(r);
        when(userService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "userService", userService);
    }

    private Ticket ticket(UUID id, String estado, String area) {
        Ticket t = new Ticket();
        t.setIdTicket(id);
        t.setTitleTicket("Ticket " + id);
        t.setCreatedAtTicket(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        Status s = new Status();
        s.setNameStatus(estado);
        t.setStatusTicket(s);
        Priority p = new Priority();
        p.setNamePriority("MEDIA");
        t.setPriorityTicket(p);
        Area a = new Area();
        a.setNameArea(area);
        t.setAreaTicket(a);
        Category c = new Category();
        c.setNameCategory("Soporte");
        t.setCategoryTicket(c);
        return t;
    }
}
