package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.TicketSla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketSlaRepository extends JpaRepository<TicketSla, UUID> {

    Optional<TicketSla> findByTicketIdTicket(UUID ticketId);

    /** SLA de varios tickets en una sola consulta (el tablero lo pedía ticket a ticket). */
    List<TicketSla> findByTicketIdTicketIn(java.util.Collection<UUID> ticketIds);

    @Query("SELECT t FROM TicketSla t WHERE t.ticket.deletedTicket = false " +
           "AND t.ticket.statusTicket.nameStatus NOT IN ('RESUELTO','CERRADO') " +
           "AND t.responseDueAt BETWEEN :from AND :to")
    List<TicketSla> findDueForResponseBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT t FROM TicketSla t WHERE t.ticket.deletedTicket = false " +
           "AND t.ticket.statusTicket.nameStatus NOT IN ('RESUELTO','CERRADO') " +
           "AND t.resolutionDueAt < :now AND t.resolutionBreached = false")
    List<TicketSla> findResolutionBreached(@Param("now") OffsetDateTime now);

    @Query("SELECT t FROM TicketSla t WHERE t.ticket.deletedTicket = false " +
           "AND t.ticket.statusTicket.nameStatus NOT IN ('RESUELTO','CERRADO') " +
           "AND t.responseDueAt < :now AND t.responseCompletedAt IS NULL")
    List<TicketSla> findResponseBreached(@Param("now") OffsetDateTime now);

    /**
     * SLA de los tickets abiertos (sin resolver ni cerrar), con lo que pinta el
     * panel de SLA en tiempo real cargado de una vez (V49).
     */
    @Query("SELECT t FROM TicketSla t JOIN FETCH t.ticket tk JOIN FETCH t.slaPolicy "
            + "LEFT JOIN FETCH tk.statusTicket LEFT JOIN FETCH tk.priorityTicket LEFT JOIN FETCH tk.agentTicket "
            + "LEFT JOIN FETCH tk.areaDestinoTicket "
            + "WHERE tk.deletedTicket = false AND t.resolutionCompletedAt IS NULL "
            + "AND (tk.statusTicket IS NULL OR tk.statusTicket.nameStatus NOT IN ('RESUELTO', 'CERRADO'))")
    List<TicketSla> abiertosParaPanel();
}
