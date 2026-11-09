package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    /** El número correlativo que ve la gente ("#43"); la clave interna es el uuid. */
    java.util.Optional<Ticket> findByNumberTicketAndDeletedTicketFalse(Long numberTicket);


    /*
     * Las listas traen de una vez lo que pinta cada fila. Las relaciones son
     * perezosas: sin esto, cada ticket lanzaba varias consultas más (solicitante,
     * agente, área, estado, prioridad...), y la lista crecía con el número de filas.
     */
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"userTicket", "userTicket.rolUser", "userTicket.area", "userTicket.sucursal", "agentTicket", "agentTicket.rolUser", "agentTicket.area", "agentTicket.sucursal", "teamTicket", "areaTicket", "sucursalTicket", "categoryTicket", "typeTicket", "statusTicket", "priorityTicket"})
    org.springframework.data.domain.Page<Ticket> findAll(
            org.springframework.data.jpa.domain.Specification<Ticket> spec,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"userTicket", "userTicket.rolUser", "userTicket.area", "userTicket.sucursal", "agentTicket", "agentTicket.rolUser", "agentTicket.area", "agentTicket.sucursal", "teamTicket", "areaTicket", "sucursalTicket", "categoryTicket", "typeTicket", "statusTicket", "priorityTicket"})
    List<Ticket> findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(UUID userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"userTicket", "userTicket.rolUser", "userTicket.area", "userTicket.sucursal", "agentTicket", "agentTicket.rolUser", "agentTicket.area", "agentTicket.sucursal", "teamTicket", "areaTicket", "sucursalTicket", "categoryTicket", "typeTicket", "statusTicket", "priorityTicket"})
    List<Ticket> findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(UUID agentId);

    /** Tickets aún en curso para el calendario (lo resuelto o cerrado ya no se planifica). */
    @Query("SELECT t FROM Ticket t JOIN FETCH t.statusTicket s JOIN FETCH t.priorityTicket "
            + "WHERE t.deletedTicket = false AND s.nameStatus NOT IN ('RESUELTO', 'CERRADO') "
            + "AND (:soloAgente = false OR t.agentTicket.idUser = :agenteId) "
            + "AND t.createdAtTicket <= :hasta")
    List<Ticket> findEnCursoParaCalendario(@Param("soloAgente") boolean soloAgente,
                                           @Param("agenteId") UUID agenteId,
                                           @Param("hasta") OffsetDateTime hasta);

    List<Ticket> findByDeletedTicketFalseOrderByCreatedAtTicketDesc();

    List<Ticket> findByStatusTicketNameStatusAndDeletedTicketFalse(String status);

    long countByCreatedAtTicketBetween(OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus NOT IN ('CERRADO') ORDER BY t.createdAtTicket DESC")
    List<Ticket> findOpenTickets();

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus NOT IN ('CERRADO') " +
           "AND t.userTicket.idUser = :userId ORDER BY t.createdAtTicket DESC")
    List<Ticket> findOpenTicketsByUser(@Param("userId") UUID userId);

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.agentTicket.idUser = :agentId ORDER BY t.createdAtTicket DESC")
    List<Ticket> findOpenTicketsByAgent(@Param("agentId") UUID agentId);

    /**
     * Poblacion completa (incluye CERRADO) del agente en el rango. El tablero
     * necesita los mismos datos que usa para el total; antes los graficos se
     * armaban con findOpenTicketsByAgent y no cuadraban con el total, porque los
     * tickets ya cerrados del agente desaparecian de los graficos.
     */
    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket >= :from AND t.createdAtTicket < :to " +
           "AND t.agentTicket.idUser = :agentId ORDER BY t.createdAtTicket DESC")
    List<Ticket> findTicketsBetweenByAgent(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                           @Param("agentId") UUID agentId);

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket >= :from AND t.createdAtTicket < :to " +
           "AND t.userTicket.idUser = :userId ORDER BY t.createdAtTicket DESC")
    List<Ticket> findTicketsBetweenByUser(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                          @Param("userId") UUID userId);

    @Query("SELECT t FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket >= :from AND t.createdAtTicket < :to " +
           "ORDER BY t.createdAtTicket DESC")
    List<Ticket> findTicketsBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.createdAtTicket BETWEEN :from AND :to")
    long countCreatedBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedTicket = false " +
           "AND t.statusTicket.nameStatus = 'RESUELTO' " +
           "AND t.createdAtTicket BETWEEN :from AND :to")
    long countResolvedBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
/**
         * Tickets no eliminados que apuntan a una sede. Se usa para impedir el
         * borrado logico de una sede con historial, en lugar de perderlo en cascada.
         *
         * <p>El guion bajo no es decorativo: sin él, Spring Data leería
         * {@code sucursalTicketId} como la propiedad {@code id} de la entidad
         * Sucursal, que se llama {@code idSucursal}, y el arranque falla.
         */
        long countBySucursalTicket_IdSucursalAndDeletedTicketFalse(UUID id);
}