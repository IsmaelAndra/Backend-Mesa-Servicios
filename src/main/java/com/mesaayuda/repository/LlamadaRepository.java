package com.mesaayuda.repository;

import com.mesaayuda.entity.Llamada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LlamadaRepository extends JpaRepository<Llamada, UUID> {

    /**
     * Llamadas pendientes más antiguas, bloqueadas para esta transacción. Con
     * SKIP LOCKED, si algún día hay dos puentes, nunca toman la misma.
     */
    @Query(value = "SELECT * FROM llamada WHERE status_llamada = 'PENDIENTE' "
            + "ORDER BY created_at_llamada LIMIT :limite FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Llamada> reclamarPendientes(@Param("limite") int limite);

    /** Pendientes que nadie recogió a tiempo: el agente ya no está esperando esa llamada. */
    @Modifying
    @Query("UPDATE Llamada l SET l.statusLlamada = 'VENCIDA', l.updatedAtLlamada = :ahora, "
            + "l.errorLlamada = 'El puente de telefonía no la recogió a tiempo' "
            + "WHERE l.statusLlamada = 'PENDIENTE' AND l.createdAtLlamada < :limite")
    int vencerPendientes(@Param("limite") OffsetDateTime limite, @Param("ahora") OffsetDateTime ahora);

    /** Enviadas al puente que nunca devolvieron resultado. */
    @Modifying
    @Query("UPDATE Llamada l SET l.statusLlamada = 'ERROR', l.updatedAtLlamada = :ahora, "
            + "l.errorLlamada = 'El puente no informó el resultado' "
            + "WHERE l.statusLlamada = 'ENVIADA' AND l.updatedAtLlamada < :limite")
    int cerrarEnviadasSinRespuesta(@Param("limite") OffsetDateTime limite, @Param("ahora") OffsetDateTime ahora);

    /** Últimas llamadas, para Administración → Telefonía. */
    List<Llamada> findAllByOrderByCreatedAtLlamadaDesc(org.springframework.data.domain.Pageable pagina);

    /** Última llamada del agente, para no duplicar si pulsa dos veces seguidas. */
    Llamada findTopByCallerUserIdOrderByCreatedAtLlamadaDesc(UUID callerUserId);
}
