package com.mesaayuda.dto;

import com.mesaayuda.entity.Workflow;
import java.util.UUID;
import com.mesaayuda.entity.WorkflowVersion;

import java.util.UUID;
import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Detalle de un workflow: metadatos, disparador y la lista de versiones.
 *
 * <p>Se devuelve el grafo de cada versión, no solo el vigente. Ver las diferencias
 * entre la versión publicada y la anterior es la única forma de saber qué cambió
 * justo antes de que un proceso empezara a comportarse distinto.
 */
public record WorkflowDetailResponse(
        UUID id,
        String name,
        String description,
        String status,
        String triggerType,
        String triggerConfig,
        Integer currentVersion,
        boolean active,
        Long runCount,
        String authorityScope,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        java.util.List<VersionResponse> versions
) {

    public static WorkflowDetailResponse from(Workflow w,
                                              java.util.List<WorkflowVersion> versiones) {
        return new WorkflowDetailResponse(
                w.getIdWorkflow(),
                w.getNameWorkflow(),
                w.getDescriptionWorkflow(),
                w.getStatusWorkflow(),
                w.getTriggerType(),
                w.getTriggerConfig(),
                w.getCurrentVersion(),
                w.estaActivo(),
                w.getRunCountWorkflow(),
                w.getAuthorityScope(),
                w.getCreatedByWorkflow(),
                w.getCreatedAtWorkflow(),
                w.getUpdatedAtWorkflow(),
                versiones == null ? java.util.List.of()
                        : versiones.stream().map(VersionResponse::from).toList());
    }

    /** Una versión publicada. */
    public record VersionResponse(
            UUID id,
            Integer version,
            String definition,
            boolean current,
            UUID publishedBy,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt
    ) {

        public static VersionResponse from(WorkflowVersion v) {
            return new VersionResponse(
                    v.getIdWorkflowVersion(),
                    v.getVersionWorkflow(),
                    v.getDefinitionWorkflow(),
                    Boolean.TRUE.equals(v.getIsActiveVersion()),
                    v.getCreatedByWorkflow(),
                    v.getPublishedAtWorkflow(),
                    v.getCreatedAtWorkflow());
        }
    }
}