package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.WorkflowEventDedup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface WorkflowEventDedupRepository extends JpaRepository<WorkflowEventDedup, UUID> {

    /**
     * Intenta reservar el evento para este workflow.
     *
     * <p>Devuelve 0 si la clave ya existía, y en ese caso no hay que hacer nada:
     * es otro disparo del mismo evento, que es justo lo que se quiere descartar.
     *
     * <p>El INSERT lleva {@code ON CONFLICT DO NOTHING} contra la clave única, y no
     * un SELECT previo. Es la diferencia entre "casi nunca duplica" y "nunca
     * duplica": con el SELECT, dos hilos pueden pasar la comprobación a la vez.
     */
    @Modifying
    @Query(value = "INSERT INTO workflow_event_dedup "
            + "(workflow_workflow, correlation_key, execution_execution) "
            + "VALUES (:workflowId, :clave, :executionId) "
            + "ON CONFLICT (workflow_workflow, correlation_key) DO NOTHING",
            nativeQuery = true)
    int reservarEvento(@Param("workflowId") UUID workflowId,
                       @Param("clave") String clave,
                       @Param("executionId") UUID executionId);

    /** Limpieza de marcas antiguas para que la tabla no crezca sin fin. */
    @Modifying
    @Query("DELETE FROM WorkflowEventDedup d WHERE d.createdAtEventDedup < :limite")
    int borrarAntiguos(@Param("limite") OffsetDateTime limite);

    long countByWorkflowWorkflow(UUID workflowId);

    @Modifying
    @Query("DELETE FROM WorkflowEventDedup d WHERE d.executionExecution = :executionId")
    int borrarDeEjecucion(@Param("executionId") UUID executionId);
}
