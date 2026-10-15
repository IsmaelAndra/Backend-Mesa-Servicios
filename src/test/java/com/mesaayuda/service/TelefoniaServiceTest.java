package com.mesaayuda.service;

import com.mesaayuda.entity.Llamada;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.LlamadaRepository;
import com.mesaayuda.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Llamadas entre extensiones: quién puede llamar, a qué, y el canal del puente. */
class TelefoniaServiceTest {

    private static final String TOKEN = "t".repeat(40);

    private LlamadaRepository llamadas;
    private TicketRepository tickets;
    private UserService usuarios;
    private TicketAccessPolicy politica;
    private TelefoniaService servicio;
    private User agente;
    private User solicitante;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        llamadas = mock(LlamadaRepository.class);
        tickets = mock(TicketRepository.class);
        usuarios = mock(UserService.class);
        politica = mock(TicketAccessPolicy.class);
        servicio = new TelefoniaService(llamadas, tickets, usuarios, politica, mock(AuditService.class), TOKEN);

        agente = persona(id(1), "AGENT", "182");
        solicitante = persona(id(2), "USER", "181");
        ticket = new Ticket();
        ticket.setIdTicket(id(50));
        ticket.setUserTicket(solicitante);
        when(tickets.findById(id(50))).thenReturn(Optional.of(ticket));
        when(usuarios.getCurrentUser()).thenReturn(agente);
        when(politica.esPersonal(agente)).thenReturn(true);
        when(politica.puedeLeer(ticket, agente)).thenReturn(true);
        when(llamadas.save(any(Llamada.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static User persona(java.util.UUID uid, String rol, String extension) {
        Role r = new Role();
        r.setNameRole(rol);
        User u = new User();
        u.setIdUser(uid);
        u.setRolUser(r);
        u.setCorporateEmailUser(rol.toLowerCase() + "@coac.test");
        u.setExtensionUser(extension);
        return u;
    }

    @Test
    void elAgenteLlamaDeSuExtensionALaDelSolicitante() {
        Llamada l = servicio.llamarAlSolicitante(id(50));

        assertEquals("182", l.getCallerExtension());
        assertEquals("181", l.getCalleeExtension());
        assertEquals(Llamada.PENDIENTE, l.getStatusLlamada());
    }

    @Test
    void unSolicitanteNoPuedeLlamar() {
        when(politica.esPersonal(agente)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> servicio.llamarAlSolicitante(id(50)));
        verify(llamadas, never()).save(any());
    }

    @Test
    void sinExtensionDeAlgunaDeLasPartesNoSeLlama() {
        solicitante.setExtensionUser(null);
        assertThrows(BadRequestException.class, () -> servicio.llamarAlSolicitante(id(50)));

        solicitante.setExtensionUser("181");
        agente.setExtensionUser("18a; hangup");
        assertThrows(BadRequestException.class, () -> servicio.llamarAlSolicitante(id(50)),
                "una extension que no son solo digitos no llega a la central");
    }

    @Test
    void dosClicsSeguidosNoGeneranDosLlamadas() {
        Llamada anterior = new Llamada();
        anterior.setCalleeExtension("181");
        anterior.setStatusLlamada(Llamada.PENDIENTE);
        anterior.setCreatedAtLlamada(OffsetDateTime.now().minusSeconds(2));
        when(llamadas.findTopByCallerUserIdOrderByCreatedAtLlamadaDesc(id(1))).thenReturn(anterior);

        assertSame(anterior, servicio.llamarAlSolicitante(id(50)));
        verify(llamadas, never()).save(any());
    }

    @Test
    void sinTokenConfiguradoLaTelefoniaEstaApagada() {
        TelefoniaService apagado = new TelefoniaService(llamadas, tickets, usuarios, politica,
                mock(AuditService.class), "");
        assertFalse(apagado.habilitada());
        assertTrue(servicio.habilitada());
        assertThrows(BadRequestException.class, () -> apagado.llamarAlSolicitante(id(50)));
    }

    @Test
    void elPuenteConTokenEquivocadoNoRecibeNada() {
        assertThrows(AccessDeniedException.class, () -> servicio.recogerPendientes("otro"));
        assertThrows(AccessDeniedException.class, () -> servicio.recogerPendientes(null));
        verify(llamadas, never()).reclamarPendientes(anyInt());
    }

    @Test
    void elPuenteRecogeLasPendientesYLasMarcaEnviadas() {
        Llamada l = new Llamada();
        l.setStatusLlamada(Llamada.PENDIENTE);
        when(llamadas.reclamarPendientes(anyInt())).thenReturn(List.of(l));
        when(llamadas.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<Llamada> lote = servicio.recogerPendientes(TOKEN);

        assertEquals(1, lote.size());
        assertEquals(Llamada.ENVIADA, lote.get(0).getStatusLlamada());
    }

    @Test
    void elResultadoCierraLaLlamada() {
        Llamada l = new Llamada();
        l.setStatusLlamada(Llamada.ENVIADA);
        when(llamadas.findById(id(9))).thenReturn(Optional.of(l));

        servicio.informarResultado(TOKEN, id(9), false, "status -25");

        assertEquals(Llamada.ERROR, l.getStatusLlamada());
        assertEquals("status -25", l.getErrorLlamada());
    }
}
