package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByNameWorkflowAndDeletedWorkflowFalse(String name);

    List<Workflow> findByDeletedWorkflowFalseOrderByNameWorkflowAsc();

    List<Workflow> findByStatusWorkflowAndDeletedWorkflowFalseOrderByNameWorkflowAsc(String status);

    /**
     * Búsqueda por texto para la búsqueda global.
     *
     * <p>El término llega ya saneado de {@code SearchService} (comodines LIKE
     * escapados con {@code \}); aquí solo se envuelve en {@code %...%}.
     */
    @Query("SELECT w FROM Workflow w WHERE w.deletedWorkflow = false "
            + "AND (lower(w.nameWorkflow) LIKE lower(concat('%', :q, '%')) "
            + "OR lower(coalesce(w.descriptionWorkflow, '')) LIKE lower(concat('%', :q, '%'))) "
            + "ORDER BY w.nameWorkflow ASC")
    Page<Workflow> buscarPorTexto(@Param("q") String q, Pageable pageable);

    /**
     * Workflows que disparan con un evento concreto.
     *
     * <p>Solo los activos y no borrados, porque el resto no debe ejecutarse por
     * mucho que el evento llegue. El filtro va en la consulta y no en Java para no
     * traer filas que se van a descartar.
     */
    @Query("SELECT w FROM Workflow w WHERE w.triggerType = :tipo "
            + "AND w.statusWorkflow = 'ACTIVO' AND w.deletedWorkflow = false")
    List<Workflow> findActivosPorTrigger(@Param("tipo") String tipo);

    /** Cuántas veces se ha ejecutado, para la cabecera del listado. */
    /** Programados activos cuya próxima ejecución ya llegó. */
    @Query("SELECT w FROM Workflow w WHERE w.triggerType = 'SCHEDULED' AND w.statusWorkflow = 'ACTIVO' "
            + "AND w.deletedWorkflow = false AND w.nextRunAtWorkflow IS NOT NULL "
            + "AND w.nextRunAtWorkflow <= :ahora")
    List<Workflow> findProgramadosVencidos(@Param("ahora") java.time.OffsetDateTime ahora);

    @Query("SELECT count(w) FROM Workflow w WHERE w.deletedWorkflow = false")
    long countVivos();

    /** Suma de ejecuciones, para las tarjetas de resumen. */
    @Query("SELECT coalesce(sum(w.runCountWorkflow), 0) FROM Workflow w WHERE w.deletedWorkflow = false")
    long totalEjecuciones();
}
