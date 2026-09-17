package com.mesaayuda.repository;

import com.mesaayuda.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(Long userId);

    List<Ticket> findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(Long agentId);

    List<Ticket> findByDeletedTicketFalseOrderByCreatedAtTicketDesc();

    List<Ticket> findByStatusTicketNameStatusAndDeletedTicketFalse(String status);

    long countByCreatedAtTicketBetween(OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus NOT IN ('CERRADO') ORDER BY t.createdAtTicket DESC")
    List<Ticket> findOpenTickets();

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus NOT IN ('CERRADO') " +
           "AND t.userTicket.idUser = :userId ORDER BY t.createdAtTicket DESC")
    List<Ticket> findOpenTicketsByUser(@Param("userId") Long userId);

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus NOT IN ('CERRADO') " +
           "AND t.agentTicket.idUser = :agentId ORDER BY t.createdAtTicket DESC")
    List<Ticket> findOpenTicketsByAgent(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket BETWEEN :from AND :to " +
           "AND t.userTicket.idUser = :userId")
    long countCreatedBetweenByUser(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                   @Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket BETWEEN :from AND :to " +
           "AND t.agentTicket.idUser = :agentId")
    long countCreatedBetweenByAgent(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                    @Param("agentId") Long agentId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus = 'RESUELTO' " +
           "AND t.createdAtTicket BETWEEN :from AND :to " +
           "AND t.userTicket.idUser = :userId")
    long countResolvedBetweenByUser(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                    @Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus = 'RESUELTO' " +
           "AND t.createdAtTicket BETWEEN :from AND :to " +
           "AND t.agentTicket.idUser = :agentId")
    long countResolvedBetweenByAgent(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                     @Param("agentId") Long agentId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket BETWEEN :from AND :to")
    long countCreatedBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus = 'RESUELTO' " +
           "AND t.createdAtTicket BETWEEN :from AND :to")
    long countResolvedBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}