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
import com.mesaayuda.entity.TicketRating;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketRatingRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Indicadores ampliados del tablero: tendencia frente al periodo anterior,
 * antigüedad de lo pendiente, tickets que requieren atención, carga por agente y
 * satisfacción. Y que el SLA ya no se pide ticket a ticket.
 */
class DashboardServiceIndicadoresTest {

    private DashboardService service;
    private TicketRepository tickets;
    private TicketSlaRepository slas;
    private TicketRatingRepository calificaciones;
    private final OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new DashboardService();
        tickets = Mockito.mock(TicketRepository.class);
        slas = Mockito.mock(TicketSlaRepository.class);
        calificaciones = Mockito.mock(TicketRatingRepository.class);
        ReflectionTestUtils.setField(service, "ticketRepository", tickets);
        ReflectionTestUtils.setField(service, "ticketSlaRepository", slas);
        ReflectionTestUtils.setField(service, "ticketRatingRepository", calificaciones);
        usuario("ADMIN");
    }

    @Test
    void separaElPeriodoAnteriorDeUnaSolaConsulta() {
        // Rango: los últimos 10 días. El anterior: los 10 días previos.
        LocalDate hasta = LocalDate.now(ZoneOffset.UTC);
        LocalDate desde = hasta.minusDays(9);
        Ticket actual = ticket(id(1), "ASIGNADO", ahora.minusDays(2));
        Ticket previo1 = ticket(id(2), "RESUELTO", ahora.minusDays(15));
        Ticket previo2 = ticket(id(3), "CERRADO", ahora.minusDays(16));
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(actual, previo1, previo2));

        DashboardStats s = service.getStats(null, null, null, desde, hasta);

        verify(tickets).findTicketsBetween(any(), any());
        assertEquals(1L, s.totalTickets());
        assertEquals(2L, s.prevTotalTickets());
        assertEquals(1L, s.prevResolvedTickets());
    }

    @Test
    void sinTicketsAnterioresLaTendenciaQuedaVacia() {
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(ticket(id(1), "ASIGNADO", ahora.minusDays(1))));

        DashboardStats s = service.getStats(null, null, null, null, null);

        assertNull(s.prevTotalTickets());
        assertNull(s.prevSlaComplianceRate());
    }

    @Test
    void clasificaLoPendientePorAntiguedadYDejaFueraLoTerminado() {
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(
                ticket(id(1), "ASIGNADO", ahora.minusHours(3)),
                ticket(id(2), "EN_PROCESO", ahora.minusDays(2)),
                ticket(id(3), "EN_PROCESO", ahora.minusDays(5)),
                ticket(id(4), "ASIGNADO", ahora.minusDays(20)),
                ticket(id(5), "CERRADO", ahora.minusDays(20))));

        DashboardStats s = service.getStats(null, null, null, null, null);

        assertEquals(1L, s.backlogAging().get("< 1 día"));
        assertEquals(1L, s.backlogAging().get("1-3 días"));
        assertEquals(1L, s.backlogAging().get("3-7 días"));
        assertEquals(1L, s.backlogAging().get("> 7 días"));
    }

    @Test
    void laAtencionPoneDelanteLosVencidosYLuegoLosQueVencenAntes() {
        Ticket tarde = ticket(id(1), "ASIGNADO", ahora.minusDays(1));
        Ticket vencido = ticket(id(2), "ASIGNADO", ahora.minusDays(3));
        Ticket pronto = ticket(id(3), "ASIGNADO", ahora.minusDays(1));
        Ticket cerrado = ticket(id(4), "CERRADO", ahora.minusDays(1));
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(tarde, vencido, pronto, cerrado));
        when(slas.findByTicketIdTicketIn(anyCollection())).thenReturn(List.of(
                sla(tarde, ahora.plusDays(2), false),
                sla(vencido, ahora.minusHours(5), true),
                sla(pronto, ahora.plusHours(1), false),
                sla(cerrado, ahora.minusDays(1), true)));

        DashboardStats s = service.getStats(null, null, null, null, null);

        List<UUID> orden = s.attention().stream().map(DashboardStats.TicketAtencion::ticketId).toList();
        assertEquals(List.of(id(2), id(3), id(1)), orden);
        assertTrue(s.attention().get(0).breached());
        assertFalse(s.attention().get(1).breached());
    }

    @Test
    void elSlaSePideEnLoteYNoTicketATicket() {
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(
                ticket(id(1), "ASIGNADO", ahora.minusDays(1)), ticket(id(2), "RESUELTO", ahora.minusDays(1))));

        service.getStats(null, null, null, null, null);

        verify(slas).findByTicketIdTicketIn(anyCollection());
        verify(slas, never()).findByTicketIdTicket(any(java.util.UUID.class));
    }

    @Test
    void laCargaPorAgenteSoloLaVeLaAdministracion() {
        Ticket t1 = ticket(id(1), "ASIGNADO", ahora.minusDays(1));
        Ticket t2 = ticket(id(2), "RESUELTO", ahora.minusDays(1));
        User agente = new User();
        agente.setIdUser(id(9));
        agente.setDisplayName("Ana Agente");
        t1.setAgentTicket(agente);
        t2.setAgentTicket(agente);
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(t1, t2));
        when(tickets.findTicketsBetweenByAgent(any(), any(), any(java.util.UUID.class))).thenReturn(List.of(t1, t2));

        DashboardStats admin = service.getStats(null, null, null, null, null);
        assertEquals(1, admin.agentLoad().size());
        assertEquals("Ana Agente", admin.agentLoad().get(0).name());
        assertEquals(1L, admin.agentLoad().get(0).openTickets());
        assertEquals(1L, admin.agentLoad().get(0).resolvedTickets());

        usuario("AGENT");
        DashboardStats deAgente = service.getStats(null, null, null, null, null);
        assertTrue(deAgente.agentLoad().isEmpty(), "un agente no compara su carga con la de otros");
    }

    @Test
    void laSatisfaccionEsLaMediaDeLasCalificaciones() {
        Ticket t1 = ticket(id(1), "CERRADO", ahora.minusDays(1));
        Ticket t2 = ticket(id(2), "CERRADO", ahora.minusDays(1));
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(t1, t2));
        when(calificaciones.findByTicketIdTicketIn(anyCollection())).thenReturn(List.of(nota(5), nota(4)));

        DashboardStats s = service.getStats(null, null, null, null, null);

        assertEquals(4.5, s.avgSatisfaction());
        assertEquals(2L, s.ratingsCount());
    }

    @Test
    void elMapaDeCalorTieneSieteDiasDeVeintiCuatroHoras() {
        when(tickets.findTicketsBetween(any(), any())).thenReturn(List.of(ticket(id(1), "ASIGNADO", ahora.minusDays(1))));

        DashboardStats s = service.getStats(null, null, null, null, null);

        assertEquals(7, s.activityHeatmap().size());
        s.activityHeatmap().forEach(dia -> assertEquals(24, dia.size()));
        assertEquals(1, s.activityHeatmap().stream().flatMap(List::stream).mapToInt(Integer::intValue).sum());
        assertEquals(12, s.weekLabels().size());
        assertEquals(12, s.resolvedLast12Weeks().size());
    }

    // ---------------------------------------------------------------------------

    private void usuario(String rol) {
        UserService userService = Mockito.mock(UserService.class);
        User u = new User();
        u.setIdUser(id(1));
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        when(userService.getCurrentUser()).thenReturn(u);
        ReflectionTestUtils.setField(service, "userService", userService);
    }

    private Ticket ticket(UUID id, String estado, OffsetDateTime creado) {
        Ticket t = new Ticket();
        t.setIdTicket(id);
        t.setTitleTicket("Ticket " + id);
        t.setCreatedAtTicket(creado);
        if ("CERRADO".equals(estado) || "RESUELTO".equals(estado)) {
            t.setClosingDateTicket(creado.plusHours(4));
        }
        Status s = new Status();
        s.setNameStatus(estado);
        t.setStatusTicket(s);
        Priority p = new Priority();
        p.setNamePriority("ALTA");
        t.setPriorityTicket(p);
        Area a = new Area();
        a.setNameArea("TI");
        t.setAreaTicket(a);
        Category c = new Category();
        c.setNameCategory("Soporte");
        t.setCategoryTicket(c);
        return t;
    }

    private TicketSla sla(Ticket t, OffsetDateTime vence, boolean incumplido) {
        TicketSla s = new TicketSla();
        s.setTicket(t);
        s.setResolutionDueAt(vence);
        s.setResolutionBreached(incumplido);
        return s;
    }

    private TicketRating nota(int valor) {
        TicketRating r = new TicketRating();
        r.setOverallSatisfaction(valor);
        return r;
    }
}
