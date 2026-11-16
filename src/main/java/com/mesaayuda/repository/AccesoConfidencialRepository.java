package com.mesaayuda.repository;

import com.mesaayuda.entity.AccesoConfidencial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccesoConfidencialRepository extends JpaRepository<AccesoConfidencial, UUID> {

    /** Confidenciales con acceso aprobado y vigente para la persona. */
    @Query("SELECT a.ticket.idTicket FROM AccesoConfidencial a WHERE a.solicitante.idUser = :userId "
            + "AND a.estado = 'APROBADO' AND a.venceAt > :ahora")
    List<UUID> vigentesDe(@Param("userId") UUID userId, @Param("ahora") OffsetDateTime ahora);

    /** La última solicitud de la persona sobre el ticket. */
    Optional<AccesoConfidencial> findFirstByTicketIdTicketAndSolicitanteIdUserOrderByCreatedAtAccesoDesc(
            UUID ticketId, UUID userId);

    /** Pendientes de los tickets que atienden estas áreas (lo que decide un jefe). */
    @EntityGraph(attributePaths = {"ticket", "solicitante"})
    @Query("SELECT a FROM AccesoConfidencial a WHERE a.estado = 'PENDIENTE' "
            + "AND a.ticket.areaDestinoTicket.idArea IN :areas ORDER BY a.createdAtAcceso")
    List<AccesoConfidencial> pendientesDeAreas(@Param("areas") Collection<UUID> areas);
}
