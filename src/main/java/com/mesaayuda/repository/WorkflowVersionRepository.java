package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    Optional<WorkflowVersion> findByWorkflowWorkflowAndVersionWorkflow(UUID workflowId, Integer version);

    /** Versión vigente. El índice único parcial garantiza que hay como mucho una. */
    Optional<WorkflowVersion> findByWorkflowWorkflowAndIsActiveVersionTrue(UUID workflowId);

    List<WorkflowVersion> findByWorkflowWorkflowOrderByVersionWorkflowDesc(UUID workflowId);

    /** Número de versión más alto creado hasta ahora, para numerar la siguiente. */
    Optional<WorkflowVersion> findFirstByWorkflowWorkflowOrderByVersionWorkflowDesc(UUID workflowId);
}
