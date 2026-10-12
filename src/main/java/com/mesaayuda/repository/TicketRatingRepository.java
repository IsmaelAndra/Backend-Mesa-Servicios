package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.TicketRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRatingRepository extends JpaRepository<TicketRating, UUID> {

    Optional<TicketRating> findByTicketIdTicket(UUID ticketId);

    /** Calificaciones de varios tickets en una sola consulta (satisfacción del tablero). */
    List<TicketRating> findByTicketIdTicketIn(java.util.Collection<UUID> ticketIds);

    boolean existsByTicketIdTicket(UUID ticketId);

    @Query("SELECT r FROM TicketRating r WHERE r.ticket.agentTicket.idUser = :agentId " +
           "AND r.evaluationMonth = :month")
    List<TicketRating> findByAgentAndMonth(@Param("agentId") UUID agentId, @Param("month") LocalDate month);

    @Query("SELECT r FROM TicketRating r WHERE r.ticket.userTicket.idUser = :userId")
    List<TicketRating> findByRequester(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(AVG(r.overallSatisfaction), 0) FROM TicketRating r WHERE r.evaluationMonth = :month")
    Double avgSatisfactionByMonth(@Param("month") LocalDate month);
}