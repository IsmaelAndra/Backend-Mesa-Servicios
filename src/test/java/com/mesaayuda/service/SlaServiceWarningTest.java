package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El planificador de SLA corre cada 15 minutos y recorría los vencimientos
 * notificando siempre. En la base de datos real había tickets con tres avisos
 * idénticos del mismo incumplimiento, y doce seguían reavisando.
 */
@ExtendWith(MockitoExtension.class)
class SlaServiceWarningTest {

    @Mock private com.mesaayuda.repository.SlaRuleRepository slaRuleRepository;
    @Mock private com.mesaayuda.repository.TicketSlaRepository ticketSlaRepository;
    @Mock private com.mesaayuda.repository.SlaEscalationRepository escalationRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;

    @org.mockito.Mock private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;


    @InjectMocks private SlaService slaService;

    private Ticket ticket(UUID id) {
        Ticket t = new Ticket();
        t.setIdTicket(id);
        return t;
    }

    /**
     * Construye un seguimiento cuyo porcentaje consumido sea exactamente el
     * pedido: con un plazo total fijo de 1000 minutos, haber transcurrido
     * pct*10 minutos equivale a estar en pct% del plazo.
     */
    private TicketSla seguimientoConPorcentaje(int pct) {
        Ticket t = ticket(id(7));
        OffsetDateTime ahora = OffsetDateTime.now();
        // total = 1000 minutos, elapsed = pct*10 -> pct% exacto
        TicketSla ts = new TicketSla();
        ts.setIdTicketSla(id(1));
        ts.setTicket(t);
        ts.setCreatedAt(ahora.minusMinutes(pct * 10L));
        ts.setResponseDueAt(ahora.plusMinutes(1000L - pct * 10L));
        return ts;
    }

    @Test
    @DisplayName("si ya existe el aviso de por-venir, no lo repite")
    void noRepiteElAvisoDeWarning() {
        TicketSla ts = seguimientoConPorcentaje(85);
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(ts));
        when(notificationRepository.existsByTicketIdTicketAndTypeNotification(id(7), "SLA_WARNING"))
                .thenReturn(true);

        slaService.checkSlaWarnings();

        verify(notificationService, never()).notifySlaWarning(any(), anyInt());
    }

    @Test
    @DisplayName("si no existe, emite el aviso de por-venir")
    void emiteWarningLaPrimeraVez() {
        TicketSla ts = seguimientoConPorcentaje(85);
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(ts));
        when(notificationRepository.existsByTicketIdTicketAndTypeNotification(id(7), "SLA_WARNING"))
                .thenReturn(false);

        slaService.checkSlaWarnings();

        verify(notificationService).notifySlaWarning(eq(ts), eq(85));
    }

    @Test
    @DisplayName("un ticket puede recibir el aviso de vencido aunque ya tuviera el de por-venir")
    void warningYBreachSonAvisosDistintos() {
        // Es el caso que se rompería si la comprobación fuera global en vez de
        // por tipo: el ticket avisó al 80%, llegó al 100% y nunca se enteraba
        // del incumplimiento. Se cubre en dos pasos, porque el planificador
        // solo puede evaluar el porcentaje que tiene en ese momento.
        TicketSla en80 = seguimientoConPorcentaje(85);
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(en80));
        when(notificationRepository.existsByTicketIdTicketAndTypeNotification(id(7), "SLA_WARNING"))
                .thenReturn(true);

        slaService.checkSlaWarnings();

        // En el 85% no debe saltar nada: el aviso de por vencer ya salió.
        verify(notificationService, never()).notifySlaBreached(any());

        // Ahora el mismo ticket llega al 100% y sí debe avisar del vencimiento.
        TicketSla en100 = seguimientoConPorcentaje(100);
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(en100));
        when(notificationRepository.existsByTicketIdTicketAndTypeNotification(id(7), "SLA_BREACHED"))
                .thenReturn(false);
        // escalate() sale antes si el seguimiento no trae política, y aquí no la
        // tiene, así que el repositorio de escalaciones no llega a consultarse.
        slaService.checkSlaWarnings();

        verify(notificationService).notifySlaBreached(eq(en100));
    }

    @Test
    @DisplayName("si ya se avisó del vencimiento, no repite ni vuelve a escalar")
    void noRepiteElIncumplimientoNiLaEscalacion() {
        TicketSla ts = seguimientoConPorcentaje(100);
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(ts));
        when(notificationRepository.existsByTicketIdTicketAndTypeNotification(id(7), "SLA_BREACHED"))
                .thenReturn(true);

        slaService.checkSlaWarnings();

        verify(notificationService, never()).notifySlaBreached(any());
        verify(escalationRepository, never()).findBySlaPolicyIdSlaPolicy(any());
    }

    @Test
    @DisplayName("una ejecución normal no revienta con datos incompletos")
    void toleraSeguimientosSinFechas() {
        TicketSla ts = new TicketSla();
        ts.setTicket(ticket(id(7)));
        ts.setCreatedAt(null);
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(ts));

        org.assertj.core.api.Assertions.assertThatCode(() -> slaService.checkSlaWarnings())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un plazo ya vencido (total = 0) no genera avisos")
    void ignoraPlazosCero() {
        TicketSla ts = new TicketSla();
        ts.setTicket(ticket(id(7)));
        ts.setCreatedAt(OffsetDateTime.now().minusHours(1));
        ts.setResponseDueAt(ts.getCreatedAt());
        when(ticketSlaRepository.findDueForResponseBetween(any(), any())).thenReturn(List.of(ts));

        slaService.checkSlaWarnings();

        verify(notificationService, never()).notifySlaWarning(any(), anyInt());
        verify(notificationService, never()).notifySlaBreached(any());
    }
}
