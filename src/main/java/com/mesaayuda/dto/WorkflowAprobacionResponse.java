package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Una aprobación pendiente, tal y como la ve quien tiene que decidir.
 *
 * <p>No incluye el grafo ni las variables de la ejecución: quien aprueba no tiene
 * por qué ver la lógica interna del workflow, solo qué se le pide y sobre qué.
 */
public record WorkflowAprobacionResponse(
        UUID id,
        UUID executionId,
        String workflowName,
        String nodeId,
        String solicitud,
        String requiredRole,
        String entityType,
        UUID entityId,
        String entityLabel,
        OffsetDateTime createdAt,
        OffsetDateTime deadline
) {
}
