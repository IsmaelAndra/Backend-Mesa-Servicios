package com.mesaayuda.dto;

import com.mesaayuda.entity.Workflow;

import java.util.UUID;
import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Vista de un workflow en el listado.
 *
 * <p>No expone {@code triggerConfig} ni la definición: el listado es para elegir
 * uno, no para auditarlo. Para eso está el detalle con sus versiones.
 */
public record WorkflowSummaryResponse(
        UUID id,
        String name,
        String description,
        String status,
        String triggerType,
        Integer currentVersion,
        boolean active,
        Long runCount,
        String authorityScope,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastRun,
        // Solo en los programados: cuándo toca la siguiente ejecución.
        OffsetDateTime nextRunAt
) {

    public static WorkflowSummaryResponse from(Workflow w) {
        return new WorkflowSummaryResponse(
                w.getIdWorkflow(),
                w.getNameWorkflow(),
                w.getDescriptionWorkflow(),
                w.getStatusWorkflow(),
                w.getTriggerType(),
                w.getCurrentVersion(),
                w.estaActivo(),
                w.getRunCountWorkflow(),
                w.getAuthorityScope(),
                w.getCreatedAtWorkflow(),
                w.getUpdatedAtWorkflow(),
                w.getLastRunWorkflow(),
                w.getNextRunAtWorkflow());
    }
}