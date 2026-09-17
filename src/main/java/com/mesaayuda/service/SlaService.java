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

@Service
@Transactional
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    @Autowired
    private SlaRuleRepository slaRuleRepository;

    @Autowired
    private TicketSlaRepository ticketSlaRepository;

    @Autowired
    private SlaEscalationRepository escalationRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Aplica la política SLA correspondiente a un ticket según
     * área + prioridad + categoría (regla más específica primero).
     */
    public TicketSla applySlaToTicket(Ticket ticket) {
        Long areaId = ticket.getAreaTicket().getIdArea();
        Long priorityId = ticket.getPriorityTicket().getIdPriority();
        Long categoryId = ticket.getCategoryTicket() != null ? ticket.getCategoryTicket().getIdCategory() : null;
        Long typeId = ticket.getTypeTicket() != null ? ticket.getTypeTicket().getIdTicketType() : null;

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

        TicketSla ticketSla = new TicketSla();
        ticketSla.setTicket(ticket);

        if (rule.isPresent()) {
            SlaPolicy policy = rule.get().getSlaPolicy();
            ticketSla.setSlaPolicy(policy);
            ticketSla.setResponseDueAt(OffsetDateTime.now().plusMinutes(policy.getResponseTimeMinutes()));
            ticketSla.setResolutionDueAt(OffsetDateTime.now().plusMinutes(policy.getResolutionTimeMinutes()));
            log.info("SLA '{}' aplicado al ticket #{}", policy.getNameSlaPolicy(), ticket.getIdTicket());
        } else {
            log.warn("Sin regla SLA para ticket #{} (área={}, prioridad={})",
                    ticket.getIdTicket(),
                    ticket.getAreaTicket().getNameArea(),
                    ticket.getPriorityTicket().getNamePriority());
        }
        return ticketSlaRepository.save(ticketSla);
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
            if (pct >= 80 && pct < 100) {
                notificationService.notifySlaWarning(ts, pct);
            }
            if (pct >= 100) {
                notificationService.notifySlaBreached(ts);
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