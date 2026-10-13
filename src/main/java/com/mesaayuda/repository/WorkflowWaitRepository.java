package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.WorkflowWait;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkflowWaitRepository extends JpaRepository<WorkflowWait, UUID> {

    Optional<WorkflowWait> findFirstByExecutionExecutionAndStatusWaitOrderByCreatedAtWaitAsc(
            UUID executionId, String status);

    List<WorkflowWait> findByExecutionExecutionOrderByCreatedAtWaitAsc(UUID executionId);

    /**
     * Esperas vencidas: las de tiempo que ya cumplieron su plazo y las
     * aprobaciones con fecha límite que nadie decidió a tiempo.
     *
     * <p>Antes se excluían las aprobaciones, y como nada más las cerraba, una con
     * "horas_limite" se quedaba pendiente para siempre aunque el plazo hubiera
     * pasado. Una aprobación sin fecha límite no entra: resume_at es null.
     */
    @Query("SELECT w FROM WorkflowWait w WHERE w.statusWait = 'PENDING' "
            + "AND w.resumeAtWait IS NOT NULL "
            + "AND w.resumeAtWait <= :ahora ORDER BY w.resumeAtWait")
    List<WorkflowWait> findVencidas(@Param("ahora") OffsetDateTime ahora, Pageable pageable);

    long countByExecutionExecutionAndStatusWait(UUID executionId, String status);

    /** Aprobaciones que le esperan a un usuario concreto. */
    List<WorkflowWait> findByApproverUserWaitAndStatusWaitOrderByCreatedAtWaitAsc(UUID userId, String status);

    /** Aprobaciones que le esperan a quien tiene un rol. */
    @Query("SELECT w FROM WorkflowWait w WHERE w.approverRoleWait = :rol "
            + "AND w.statusWait = 'PENDING' ORDER BY w.createdAtWait ASC, w.idWait ASC")
    List<WorkflowWait> findPorRol(@Param("rol") String rol);

    /** Limpieza de esperas ya resueltas, para que la tabla no crezca sin fin. */
    @Modifying
    @Query("DELETE FROM WorkflowWait w WHERE w.statusWait <> 'PENDING' "
            + "AND w.decidedAtWait < :limite")
    int borrarResueltas(@Param("limite") OffsetDateTime limite);

    @Modifying
    @Query("DELETE FROM WorkflowWait w WHERE w.executionExecution = :executionId")
    int borrarDeEjecucion(@Param("executionId") UUID executionId);

    /** Aprobaciones pendientes, para la bandeja (el filtro por permiso lo hace el servicio). */
    @Query("SELECT w FROM WorkflowWait w WHERE w.kindWait = 'APPROVAL' AND w.statusWait = 'PENDING' "
            + "ORDER BY w.createdAtWait ASC")
    List<WorkflowWait> aprobacionesPendientes();

    /** Aprobaciones recientes (pendientes y decididas), para "Mis solicitudes". */
    @Query("SELECT w FROM WorkflowWait w WHERE w.kindWait = 'APPROVAL' "
            + "ORDER BY w.createdAtWait DESC, w.idWait DESC")
    List<WorkflowWait> aprobacionesRecientes(Pageable pageable);

    /**
     * Cierra una aprobación solo si sigue pendiente. Devuelve 0 si otra persona
     * la decidió antes: dos aprobadores pulsando a la vez no pueden decidir los dos.
     */
    @Modifying
    @Query("UPDATE WorkflowWait w SET w.statusWait = :estado, w.decidedByWait = :usuario, "
            + "w.decidedAtWait = :ahora, w.commentWait = :comentario "
            + "WHERE w.idWait = :id AND w.statusWait = 'PENDING'")
    int decidir(@Param("id") UUID id, @Param("estado") String estado, @Param("usuario") UUID usuario,
                @Param("ahora") OffsetDateTime ahora, @Param("comentario") String comentario);
}
