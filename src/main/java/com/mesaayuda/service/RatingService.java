package com.mesaayuda.service;

import com.mesaayuda.dto.RatingRequest;
import com.mesaayuda.dto.RatingResponse;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketRating;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.TicketRatingRepository;
import com.mesaayuda.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    @Autowired
    private TicketRatingRepository ratingRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    /**
     * Calificación de tickets: solo el usuario propietario y cuando el ticket
     * está RESUELTO o CERRADO. Un ticket solo puede calificarse una vez.
     */
    public RatingResponse rate(Long ticketId, RatingRequest request) {
        User current = userService.getCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> !t.getDeletedTicket())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", ticketId));

        if (!ticket.getUserTicket().getIdUser().equals(current.getIdUser())) {
            throw new BadRequestException("Solo el solicitante puede calificar su ticket");
        }
        String status = ticket.getStatusTicket().getNameStatus();
        if (!"RESUELTO".equals(status) && !"CERRADO".equals(status)) {
            throw new BadRequestException("El ticket debe estar RESUELTO o CERRADO para calificarlo");
        }
        if (ratingRepository.existsByTicketIdTicket(ticketId)) {
            throw new BadRequestException("El ticket ya fue calificado");
        }

        if (request.overallSatisfaction() == null) {
            throw new BadRequestException("La satisfacción general es obligatoria");
        }

        TicketRating rating = new TicketRating();
        rating.setTicket(ticket);
        rating.setUser(current);
        rating.setRequirementsMet(request.requirementsMet());
        rating.setAllRequirementsMet(Boolean.TRUE.equals(request.allRequirementsMet()));
        rating.setResponseTimeRating(request.responseTimeRating());
        rating.setResolutionQuality(request.resolutionQuality());
        rating.setAgentCommunication(request.agentCommunication());
        rating.setOverallSatisfaction(request.overallSatisfaction());
        rating.setComments(request.comments());
        rating.setEvaluationMonth(LocalDate.now().withDayOfMonth(1));
        rating = ratingRepository.save(rating);

        auditService.record(ticket, "RATING",
                null, "req=" + request.requirementsMet() + ", sat=" + request.overallSatisfaction(),
                "Calificación del usuario");
        log.info("Ticket #{} calificado por {}", ticketId, current.getCorporateEmailUser());
        return toResponse(rating);
    }

    @Transactional(readOnly = true)
    public RatingResponse getByTicket(Long ticketId) {
        return ratingRepository.findByTicketIdTicket(ticketId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Calificación", "ticket", ticketId));
    }

    private RatingResponse toResponse(TicketRating r) {
        return new RatingResponse(
                r.getIdRating(),
                r.getTicket().getIdTicket(),
                r.getRequirementsMet(),
                r.getAllRequirementsMet(),
                r.getResponseTimeRating(),
                r.getResolutionQuality(),
                r.getAgentCommunication(),
                r.getOverallSatisfaction(),
                r.getComments(),
                r.getRatedAt());
    }
}