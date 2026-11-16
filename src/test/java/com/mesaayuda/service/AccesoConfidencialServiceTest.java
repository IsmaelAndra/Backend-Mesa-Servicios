package com.mesaayuda.service;

import com.mesaayuda.dto.AccesoConfidencialResponse;
import com.mesaayuda.entity.AccesoConfidencial;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AccesoConfidencialRepository;
import com.mesaayuda.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Acceso de emergencia del súper admin / auditor a un confidencial, decidido por el jefe. */
@ExtendWith(MockitoExtension.class)
class AccesoConfidencialServiceTest {

    private static final UUID TECNOLOGIA = id(1);

    @Mock private AccesoConfidencialRepository repo;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserService userService;
    @Mock private TicketAccessPolicy accessPolicy;
    @Mock private JefaturaService jefaturaService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private SystemAuditService systemAuditService;

    private AccesoConfidencialService servicio;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        servicio = new AccesoConfidencialService(repo, ticketRepository, userService, accessPolicy, jefaturaService,
                notificationService, auditService, systemAuditService);
        Area area = new Area();
        area.setIdArea(TECNOLOGIA);
        ticket = new Ticket();
        ticket.setIdTicket(id(50));
        ticket.setNumberTicket(43L);
        ticket.setDeletedTicket(false);
        ticket.setConfidencialTicket(true);
        ticket.setAreaDestinoTicket(area);
        lenient().when(ticketRepository.findById(ticket.getIdTicket())).thenReturn(Optional.of(ticket));
        lenient().when(repo.save(any(AccesoConfidencial.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User como(int n, String rol) {
        User u = new User();
        u.setIdUser(id(n));
        u.setCorporateEmailUser("p" + n + "@coac.test");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        when(userService.getCurrentUser()).thenReturn(u);
        return u;
    }

    @Test
    @DisplayName("el súper admin pide acceso y se avisa a los jefes del área")
    void elSuperAdminPideAcceso() {
        como(2, "SUPER_ADMIN");
        User jefe = new User();
        jefe.setIdUser(id(8));
        when(jefaturaService.jefesDe(TECNOLOGIA)).thenReturn(List.of(jefe));

        AccesoConfidencialResponse r = servicio.solicitar(ticket.getIdTicket(), "Revisión de una queja formal");

        assertEquals("PENDIENTE", r.estado());
        verify(notificationService).notifyConPlantilla(eq(ticket), eq(jefe), eq("ACCESO_CONFIDENCIAL"), any(), any(), isNull());
        verify(auditService).record(eq(ticket), eq("CONFIDENTIAL_ACCESS_REQUEST"), any(), any(), any());
    }

    @Test
    @DisplayName("un agente no pide acceso de emergencia")
    void unAgenteNoPide() {
        como(4, "AGENT");
        assertThrows(AccessDeniedException.class,
                () -> servicio.solicitar(ticket.getIdTicket(), "Quiero ver este ticket ajeno"));
    }

    @Test
    @DisplayName("sin un motivo suficiente no se pide")
    void sinMotivoNoSePide() {
        como(2, "AUDITOR");
        assertThrows(BadRequestException.class, () -> servicio.solicitar(ticket.getIdTicket(), "ver"));
    }

    @Test
    @DisplayName("un ticket normal no necesita acceso de emergencia")
    void ticketNormalSeRechaza() {
        como(2, "SUPER_ADMIN");
        ticket.setConfidencialTicket(false);
        assertThrows(BadRequestException.class,
                () -> servicio.solicitar(ticket.getIdTicket(), "Revisión de una queja formal"));
    }

    private AccesoConfidencial pendiente() {
        User solicitante = new User();
        solicitante.setIdUser(id(2));
        solicitante.setCorporateEmailUser("superadmin@coac.test");
        AccesoConfidencial a = new AccesoConfidencial();
        a.setIdAcceso(id(70));
        a.setTicket(ticket);
        a.setSolicitante(solicitante);
        a.setMotivo("Revisión de una queja formal");
        when(repo.findById(id(70))).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    @DisplayName("el jefe del área aprueba: vale unas horas y se avisa a quien lo pidió")
    void elJefeAprueba() {
        AccesoConfidencial a = pendiente();
        User jefe = como(8, "AREA_MANAGER");
        when(jefaturaService.esJefeDe(jefe, TECNOLOGIA)).thenReturn(true);

        AccesoConfidencialResponse r = servicio.decidir(a.getIdAcceso(), true, "Adelante");

        assertEquals("APROBADO", r.estado());
        assertTrue(a.getVenceAt().isAfter(OffsetDateTime.now().plusHours(AccesoConfidencialService.HORAS_ACCESO).minusMinutes(1)));
        verify(notificationService).notifyConPlantilla(eq(ticket), eq(a.getSolicitante()), eq("ACCESO_CONFIDENCIAL"), any(), any(), isNull());
        verify(auditService).record(eq(ticket), eq("CONFIDENTIAL_ACCESS_APPROVED"), any(), any(), any());
    }

    @Test
    @DisplayName("el jefe de otra área no decide")
    void elJefeDeOtraAreaNoDecide() {
        AccesoConfidencial a = pendiente();
        User otro = como(9, "AREA_MANAGER");
        when(jefaturaService.esJefeDe(otro, TECNOLOGIA)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> servicio.decidir(a.getIdAcceso(), true, null));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("una solicitud ya decidida no se vuelve a decidir")
    void yaDecididaNoSeRedecide() {
        AccesoConfidencial a = pendiente();
        a.setEstado(AccesoConfidencial.RECHAZADO);
        User jefe = como(8, "AREA_MANAGER");
        when(jefaturaService.esJefeDe(jefe, TECNOLOGIA)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> servicio.decidir(a.getIdAcceso(), true, null));
    }
}
