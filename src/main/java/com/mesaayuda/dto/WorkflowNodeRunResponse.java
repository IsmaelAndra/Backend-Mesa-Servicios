package com.mesaayuda.dto;

import com.mesaayuda.entity.WorkflowNodeRun;

import java.util.UUID;
import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Un paso recorrido en una ejecución.
 *
 * <p>Es la traza. Cuando un proceso se para a medias, esto es lo que dice en qué
 * nodo fue y qué se había hecho antes.
 */
public record WorkflowNodeRunResponse(
        UUID id,
        UUID executionId,
        String nodeId,
        String nodeType,
        String status,
        Integer sequence,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        // Configuración con la que corrió el nodo y lo que dejó en las variables
        // (JSON). Es lo que permite ver el recorrido con sus valores.
        String input,
        String output
) {

    public static WorkflowNodeRunResponse from(WorkflowNodeRun r) {
        return new WorkflowNodeRunResponse(
                r.getIdNodeRun(),
                r.getExecutionExecution(),
                r.getNodeIdNodeRun(),
                r.getNodeTypeNodeRun(),
                r.getStatusNodeRun(),
                r.getSequenceNodeRun(),
                r.getErrorMessageNodeRun(),
                r.getStartedAtNodeRun(),
                r.getFinishedAtNodeRun(),
                r.getInputNodeRun(),
                r.getOutputNodeRun());
    }
}