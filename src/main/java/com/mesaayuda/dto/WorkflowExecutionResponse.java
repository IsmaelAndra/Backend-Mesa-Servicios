package com.mesaayuda.dto;

import com.mesaayuda.entity.WorkflowExecution;
import java.util.UUID;
import com.mesaayuda.workflow.WorkflowExecutionService;

import java.util.UUID;
import java.time.Duration;
import java.util.UUID;
import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Una ejecución, tal y como se ve en el listado.
 *
 * <p>Incluye la duración porque un proceso que tarda dos horas y otro que tarda
 * dos segundos se ven igual si solo se muestra el estado. La duración es lo primero
 * que se mira cuando algo va lento.
 *
 * <p>Los campos del final se añadieron con la Fase 2; van al final para no mover
 * los que ya consumía el frontend.
 */
public record WorkflowExecutionResponse(
        UUID id,
        UUID workflowId,
        String workflowName,
        UUID versionId,
        String triggerType,
        String correlationKey,
        String status,
        String currentNode,
        Integer depth,
        Integer attempts,
        String errorNode,
        String errorMessage,
        Long durationMs,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        // Número de versión (v1, v2...), no el id interno de la fila.
        Integer versionNumber,
        UUID startedById,
        String startedByName,
        // TICKET o USUARIO, con su id, para enlazar desde el historial.
        String entityType,
        UUID entityId,
        // Ejecución cuya acción provocó esta (cascada), o null.
        UUID originExecutionId,
        // Motivo del último estado (pausa, rechazo...), en JSON.
        String result,
        // Cómo se lee la entidad: "#43" o el nombre del usuario.
        String entityLabel
) {

    public static WorkflowExecutionResponse from(WorkflowExecution e, String nombreWorkflow) {
        return from(e, nombreWorkflow, null, null, null);
    }

    public static WorkflowExecutionResponse from(WorkflowExecution e, String nombreWorkflow,
                                                 Integer numeroVersion, String nombreIniciador,
                                                 String etiquetaEntidad) {
        Duration duracion = WorkflowExecutionService.duracion(e);
        return new WorkflowExecutionResponse(
                e.getIdWorkflowExecution(),
                e.getWorkflowWorkflow(),
                nombreWorkflow,
                e.getVersionWorkflow(),
                e.getTriggerType(),
                e.getCorrelationKey(),
                e.getStatusExecution(),
                e.getCurrentNodeExecution(),
                e.getDepthExecution(),
                e.getAttemptsExecution(),
                e.getErrorNodeExecution(),
                e.getErrorMessageExecution(),
                // null en vez de 0: "no terminó" y "terminó en el acto" no son lo
                // mismo, y un cero de duración los haría indistinguibles.
                e.getFinishedAtExecution() == null ? null : duracion.toMillis(),
                e.getStartedAtExecution(),
                e.getFinishedAtExecution(),
                e.getCreatedAtExecution(),
                numeroVersion,
                e.getStartedByExecution(),
                nombreIniciador,
                e.getEntityTypeExecution(),
                e.getEntityIdExecution(),
                e.getOriginExecution(),
                e.getResultExecution(),
                etiquetaEntidad);
    }
}
