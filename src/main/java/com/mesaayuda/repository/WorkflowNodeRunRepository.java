package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.WorkflowNodeRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNodeRunRepository extends JpaRepository<WorkflowNodeRun, UUID> {

    /** Recorrido completo, en orden. Es lo que se pinta en el detalle. */
    List<WorkflowNodeRun> findByExecutionExecutionOrderBySequenceNodeRunAsc(UUID executionId);

    void deleteByExecutionExecution(UUID executionId);

    long countByExecutionExecution(UUID executionId);
}
