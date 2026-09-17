package com.mesaayuda.service;

import com.mesaayuda.dto.MonthlyEvaluationResponse;
import com.mesaayuda.dto.SupervisorEvaluationRequest;
import com.mesaayuda.entity.MonthlyEvaluation;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketRating;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.MonthlyEvaluationRepository;
import com.mesaayuda.repository.TicketRatingRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class EvaluationService {

    @Autowired
    private MonthlyEvaluationRepository evaluationRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketSlaRepository ticketSlaRepository;

    @Autowired
    private TicketRatingRepository ratingRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private com.mesaayuda.repository.UserRepository userRepo;

    @Autowired
    private NotificationService notificationService;

    /**
     * Generación de la evaluación mensual de desempeño por agente.
     * Se invoca el día 1 de cada mes (programado) o manualmente.
     */
    public MonthlyEvaluation generateForAgent(Long agentId, LocalDate month) {
        var agentEntity = userRepo.findById(agentId)
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Agente", "id", agentId));

        MonthlyEvaluation existing = evaluationRepository
                .findByAgentIdUserAndEvaluationMonth(agentId, month).orElse(null);
        if (existing == null) {
            existing = new MonthlyEvaluation();
            existing.setAgent(agentEntity);
            existing.setEvaluationMonth(month);
            existing.setStatus("PENDING");
        }

        OffsetDateTime from = month.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime to = month.plusMonths(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);

        // Tickets cerrados por el agente en el mes
        java.util.stream.Stream<Ticket> closedTickets = ticketRepository
                .findByStatusTicketNameStatusAndDeletedTicketFalse("CERRADO").stream()
                .filter(t -> t.getAgentTicket() != null
                        && t.getAgentTicket().getIdUser().equals(agentId)
                        && t.getClosingDateTicket() != null
                        && !t.getClosingDateTicket().isBefore(from)
                        && t.getClosingDateTicket().isBefore(to));
        List<Ticket> closed = closedTickets.toList();

        existing.setTicketsClosed(closed.size());

        // Tiempos de respuesta/resolución promedio
        BigDecimal avgResponse = average(closed.stream()
                .map(t -> {
                    TicketSla sla = ticketSlaRepository.findByTicketIdTicket(t.getIdTicket()).orElse(null);
                    return sla != null && sla.getResponseCompletedAt() != null
                            && sla.getResponseDueAt() != null
                            ? BigDecimal.valueOf(java.time.Duration.between(
                                    sla.getCreatedAt(), sla.getResponseCompletedAt()).toMinutes())
                            : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList());

        BigDecimal avgResolution = average(closed.stream()
                .map(t -> {
                    TicketSla sla = ticketSlaRepository.findByTicketIdTicket(t.getIdTicket()).orElse(null);
                    return sla != null && sla.getResolutionCompletedAt() != null
                            && sla.getCreatedAt() != null
                            ? BigDecimal.valueOf(java.time.Duration.between(
                                    sla.getCreatedAt(), sla.getResolutionCompletedAt()).toMinutes())
                            : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList());

        existing.setAvgResponseTimeMinutes(avgResponse);
        existing.setAvgResolutionTimeMinutes(avgResolution);

        // Cumplimiento SLA
        long metSla = closed.stream()
                .filter(t -> {
                    TicketSla sla = ticketSlaRepository.findByTicketIdTicket(t.getIdTicket()).orElse(null);
                    return sla != null && !Boolean.TRUE.equals(sla.getResolutionBreached());
                })
                .count();
        BigDecimal slaCompliance = closed.isEmpty() ? null
                : BigDecimal.valueOf(metSla * 100.0 / closed.size())
                        .setScale(2, RoundingMode.HALF_UP);
        existing.setSlaComplianceRate(slaCompliance);

        // Calificaciones del mes para el agente
        List<TicketRating> ratings = ratingRepository.findByAgentAndMonth(agentId, month);

        existing.setAvgRequirementsMet(roundAvg(ratings.stream()
                .map(TicketRating::getRequirementsMet)));
        existing.setAvgResponseTimeRating(roundAvg(ratings.stream()
                .map(TicketRating::getResponseTimeRating)));
        existing.setAvgResolutionQuality(roundAvg(ratings.stream()
                .map(TicketRating::getResolutionQuality)));
        existing.setAvgAgentCommunication(roundAvg(ratings.stream()
                .map(TicketRating::getAgentCommunication)));
        existing.setAvgOverallSatisfaction(roundAvg(ratings.stream()
                .map(TicketRating::getOverallSatisfaction)));
        existing.setRequirementsMetCount(ratings.stream()
                .filter(TicketRating::getAllRequirementsMet)
                .mapToInt(r -> 1).sum());

        BigDecimal satisfaction = roundAvg(ratings.stream()
                .map(TicketRating::getOverallSatisfaction));
        existing.setSatisfactionScore(
                satisfaction != null ? satisfaction
                        : (slaCompliance != null ? slaCompliance.divide(BigDecimal.valueOf(20), 2, RoundingMode.HALF_UP) : null));

        existing = evaluationRepository.save(existing);
        return existing;
    }

    public List<MonthlyEvaluationResponse> generateAllForMonth(LocalDate month) {
        return userService.findAgentEmails().stream().map(email -> {
            try {
                User agent = userRepo.findByCorporateEmailUser(email).orElseThrow();
                MonthlyEvaluation ev = generateForAgent(agent.getIdUser(), month);
                if ("PENDING".equals(ev.getStatus())) {
                    notificationService.notifyMonthlyEvaluation(agent);
                    ev.setStatus("COMPLETED");
                    evaluationRepository.save(ev);
                }
                return toResponse(ev);
            } catch (Exception ex) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyEvaluationResponse> findByAgent(Long agentId) {
        return evaluationRepository.findByAgentIdUserOrderByEvaluationMonthDesc(agentId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyEvaluationResponse> findByMonth(LocalDate month) {
        return evaluationRepository.findByEvaluationMonthOrderBySatisfactionScoreDesc(month)
                .stream().map(this::toResponse).toList();
    }

    public MonthlyEvaluationResponse review(Long evaluationId, SupervisorEvaluationRequest request,
                                            Long supervisorId) {
        MonthlyEvaluation ev = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluación", "id", evaluationId));
        if (request.supervisorRating() != null) {
            ev.setSupervisorRating(request.supervisorRating());
        }
        if (request.supervisorComments() != null) {
            ev.setSupervisorComments(request.supervisorComments());
        }
        ev.setEvaluatedBy(userRepo.findById(supervisorId)
                .orElseThrow(() -> new ResourceNotFoundException("Supervisor", "id", supervisorId)));
        ev.setStatus(request.status() != null ? request.status() : "REVIEWED");
        return toResponse(evaluationRepository.save(ev));
    }

    // ============================ HELPERS ============================

    private BigDecimal roundAvg(java.util.stream.Stream<Integer> values) {
        List<Integer> list = values.filter(java.util.Objects::nonNull).toList();
        if (list.isEmpty()) return null;
        double avg = list.stream().mapToInt(Integer::intValue).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private MonthlyEvaluationResponse toResponse(MonthlyEvaluation ev) {
        return new MonthlyEvaluationResponse(
                ev.getIdEvaluation(),
                ev.getAgent().getIdUser(),
                ev.getAgent().getCorporateEmailUser(),
                ev.getEvaluationMonth(),
                ev.getTicketsClosed(),
                ev.getAvgResponseTimeMinutes(),
                ev.getAvgResolutionTimeMinutes(),
                ev.getSlaComplianceRate(),
                ev.getAvgRequirementsMet(),
                ev.getAvgOverallSatisfaction(),
                ev.getSatisfactionScore(),
                ev.getSupervisorRating(),
                ev.getSupervisorComments(),
                ev.getStatus(),
                ev.getCreatedAt());
    }
}