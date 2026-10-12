package com.mesaayuda.service;

import com.mesaayuda.entity.*;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.SlaEscalationRepository;
import com.mesaayuda.repository.SlaRuleRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    /** Lazy: los handlers de workflow usan SlaService, y el publicador llega al motor. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;

    @Autowired
    private SlaRuleRepository slaRuleRepository;

    @Autowired
    private TicketSlaRepository ticketSlaRepository;

    @Autowired
    private com.mesaayuda.repository.SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private SlaEscalationRepository escalationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.mesaayuda.repository.NotificationRepository notificationRepository;

    /**
     * ¿Existe ya un aviso de este tipo para este seguimiento SLA?
     * <p>
     * El planificador revisa los vencimientos cada 15 minutos. Sin esta
     * comprobación, un ticket que se queda en la franja de aviso repetía la
     * notificación en cada pasada, y en la base había tickets con tres
     * avisos idénticos del mismo incumplimiento. Se mira el histórico en lugar
     * de añadir una columna de marca: así no hay migración y el aviso previo
     * (que ya existe en la base) también cuenta como aviso ya entregado.
     * <p>
     * Se comprueba por tipo y no globalmente a propósito: un ticket puede
     * recibir primero el aviso de "por vencer" al 80% y después el de
     * "vencido" al 100%, y son dos avisos distintos que deben salir los dos.
     */
    private boolean yaAvisado(TicketSla ticketSla, String tipo) {
        return notificationRepository.existsByTicketIdTicketAndTypeNotification(
                ticketSla.getTicket().getIdTicket(), tipo);
    }

    /**
     * Aplica la política SLA correspondiente a un ticket según
     * área + prioridad + categoría (regla más específica primero).
     */
    public TicketSla applySlaToTicket(Ticket ticket) {
        UUID areaId = ticket.getAreaTicket().getIdArea();
        UUID priorityId = ticket.getPriorityTicket().getIdPriority();
        UUID categoryId = ticket.getCategoryTicket() != null ? ticket.getCategoryTicket().getIdCategory() : null;
        UUID typeId = ticket.getTypeTicket() != null ? ticket.getTypeTicket().getIdTicketType() : null;

        Optional<SlaRule> rule = Optional.empty();
        if (categoryId != null && typeId != null) {
            rule = slaRuleRepository
                    .findByAreaIdAreaAndPriorityIdPriorityAndCategoryIdCategoryAndTypeIdTicketTypeAndActiveSlaRuleTrue(
                            areaId, priorityId, categoryId, typeId);
        }
        if (rule.isEmpty() && categoryId != null) {
            rule = slaRuleRepository
                    .findByAreaIdAreaAndPriorityIdPriorityAndCategoryIdCategoryAndTypeIsNullAndActiveSlaRuleTrue(
                            areaId, priorityId, categoryId);
        }
        if (rule.isEmpty() && typeId != null) {
            rule = slaRuleRepository
                    .findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIdTicketTypeAndActiveSlaRuleTrue(
                            areaId, priorityId, typeId);
        }
        if (rule.isEmpty()) {
            rule = slaRuleRepository
                    .findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(
                            areaId, priorityId);
        }

        // ticket_sla tiene ticket_id UNIQUE. Antes se creaba siempre una fila nueva,
        // así que volver a aplicar el SLA (el nodo SLA_APPLY de un workflow, tras
        // subir la prioridad) violaba la restricción y fallaba siempre. Ahora se
        // recalcula la que ya existe.
        TicketSla ticketSla = ticket.getIdTicket() == null ? null
                : ticketSlaRepository.findByTicketIdTicket(ticket.getIdTicket()).orElse(null);
        if (ticketSla == null) {
            ticketSla = new TicketSla();
            ticketSla.setTicket(ticket);
        }

        if (rule.isPresent()) {
            fijarPolitica(ticketSla, ticket, rule.get().getSlaPolicy());
        } else {
            log.warn("Sin regla SLA para ticket #{} (área={}, prioridad={})",
                    ticket.getIdTicket(),
                    ticket.getAreaTicket().getNameArea(),
                    ticket.getPriorityTicket().getNamePriority());
        }
        return ticketSlaRepository.save(ticketSla);
    }

    /**
     * Aplica una política concreta, sin pasar por las reglas. Es lo que hace el
     * nodo SLA_APPLY de un workflow cuando se le indica la política ("cambiar SLA").
     */
    public TicketSla aplicarPolitica(Ticket ticket, UUID politicaId) {
        SlaPolicy policy = slaPolicyRepository.findById(politicaId)
                .filter(p -> !Boolean.FALSE.equals(p.getActiveSlaPolicy()))
                .orElseThrow(() -> new com.mesaayuda.exception.BadRequestException(
                        "La política de SLA " + politicaId + " no existe o está inactiva."));
        TicketSla ticketSla = ticketSlaRepository.findByTicketIdTicket(ticket.getIdTicket()).orElseGet(() -> {
            TicketSla nuevo = new TicketSla();
            nuevo.setTicket(ticket);
            return nuevo;
        });
        fijarPolitica(ticketSla, ticket, policy);
        return ticketSlaRepository.save(ticketSla);
    }

    /**
     * Fija la política y sus vencimientos.
     *
     * <p>Los plazos cuentan desde la creación del ticket, no desde ahora: cambiar
     * el SLA de un ticket abierto hace tres horas no le regala tres horas más. Al
     * crearse, creación y ahora coinciden, así que el alta se comporta igual que
     * antes. Si con la política nueva el vencimiento queda en el futuro, se quita
     * la marca de incumplido que pudiera tener por la anterior.
     */
    private void fijarPolitica(TicketSla ticketSla, Ticket ticket, SlaPolicy policy) {
        OffsetDateTime base = ticket.getCreatedAtTicket() != null ? ticket.getCreatedAtTicket() : OffsetDateTime.now();
        ticketSla.setSlaPolicy(policy);
        ticketSla.setResponseDueAt(base.plusMinutes(policy.getResponseTimeMinutes()));
        ticketSla.setResolutionDueAt(base.plusMinutes(policy.getResolutionTimeMinutes()));
        OffsetDateTime ahora = OffsetDateTime.now();
        if (ticketSla.getResponseDueAt().isAfter(ahora)) {
            ticketSla.setResponseBreached(false);
        }
        if (ticketSla.getResolutionDueAt().isAfter(ahora)) {
            ticketSla.setResolutionBreached(false);
        }
        log.info("SLA '{}' aplicado al ticket #{}", policy.getNameSlaPolicy(), ticket.getIdTicket());
    }

    public void markResponseCompleted(Ticket ticket) {
        ticketSlaRepository.findByTicketIdTicket(ticket.getIdTicket()).ifPresent(ts -> {
            if (ts.getResponseCompletedAt() == null) {
                ts.setResponseCompletedAt(OffsetDateTime.now());
                ticketSlaRepository.save(ts);
            }
        });
    }

    public void markResolutionCompleted(Ticket ticket) {
        ticketSlaRepository.findByTicketIdTicket(ticket.getIdTicket()).ifPresent(ts -> {
            if (ts.getResolutionCompletedAt() == null) {
                ts.setResolutionCompletedAt(OffsetDateTime.now());
                ts.setResolutionBreached(ts.getResolutionDueAt() != null
                        && ts.getResolutionDueAt().isBefore(ts.getResolutionCompletedAt()));
                ticketSlaRepository.save(ts);
            }
        });
    }

    public List<TicketSla> checkSlaViolations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<TicketSla> violations = new java.util.ArrayList<>();

        ticketSlaRepository.findResponseBreached(now).forEach(ts -> {
            if (!Boolean.TRUE.equals(ts.getResponseBreached())) {
                ts.setResponseBreached(true);
                violations.add(ts);
            }
        });
        ticketSlaRepository.findResolutionBreached(now).forEach(ts -> {
            if (!Boolean.TRUE.equals(ts.getResolutionBreached())) {
                ts.setResolutionBreached(true);
                violations.add(ts);
            }
        });
        return violations;
    }

    public void checkSlaWarnings() {
        OffsetDateTime now = OffsetDateTime.now();
        ticketSlaRepository.findDueForResponseBetween(now, now.plusHours(12)).forEach(ts -> {
            if (ts.getResponseDueAt() == null) return;
            long elapsed = Duration.between(ts.getCreatedAt(), now).toMinutes();
            long total = Duration.between(ts.getCreatedAt(), ts.getResponseDueAt()).toMinutes();
            if (total <= 0) return;
            int pct = (int) Math.min(100, Math.round((elapsed * 100.0) / total));
            if (pct >= 80 && pct < 100 && !yaAvisado(ts, "SLA_WARNING")) {
                notificationService.notifySlaWarning(ts, pct);
                workflowEventPublisher.slaCercano(ts, pct);
            }
            if (pct >= 100 && !yaAvisado(ts, "SLA_BREACHED")) {
                notificationService.notifySlaBreached(ts);
                workflowEventPublisher.slaIncumplido(ts);
                escalate(ts);
            }
        });
    }

    public void escalate(TicketSla ticketSla) {
        if (ticketSla.getSlaPolicy() == null) return;
        List<SlaEscalation> escalations = escalationRepository
                .findBySlaPolicyIdSlaPolicy(ticketSla.getSlaPolicy().getIdSlaPolicy());
        escalations.stream()
                .max(Comparator.comparingInt(SlaEscalation::getTriggerPercentage))
                .ifPresent(escalation -> {
                    notificationService.notifyEscalation(ticketSla.getTicket(), escalation);
                    log.info("Ticket #{} escalado según política '{}'",
                            ticketSla.getTicket().getIdTicket(),
                            escalation.getActionType());
                });
    }
}