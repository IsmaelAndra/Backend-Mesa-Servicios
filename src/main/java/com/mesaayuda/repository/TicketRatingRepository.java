package com.mesaayuda.repository;

import com.mesaayuda.entity.TicketRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRatingRepository extends JpaRepository<TicketRating, Long> {

    Optional<TicketRating> findByTicketIdTicket(Long ticketId);

    boolean existsByTicketIdTicket(Long ticketId);

    @Query("SELECT r FROM TicketRating r WHERE r.ticket.agentTicket.idUser = :agentId " +
           "AND r.evaluationMonth = :month")
    List<TicketRating> findByAgentAndMonth(@Param("agentId") Long agentId, @Param("month") LocalDate month);

    @Query("SELECT COALESCE(AVG(r.overallSatisfaction), 0) FROM TicketRating r WHERE r.evaluationMonth = :month")
    Double avgSatisfactionByMonth(@Param("month") LocalDate month);
}