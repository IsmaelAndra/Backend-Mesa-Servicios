package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.WorkflowExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<WorkflowExecution> {

    Optional<WorkflowExecution> findByIdWorkflowExecutionAndWorkflowWorkflow(UUID id, UUID workflowId);

    /**
     * Lotes que el worker puede tomar ahora mismo.
     *
     * <p>{@code SKIP LOCKED} es lo que permite que dos instancias del backend
     * trabajen a la vez sin pisarse: la que asegura la fila la marca como tomada y
     * la otra se salta a la siguiente. Sin esto, las dos leerían la misma ejecución
     * y la ejecutarían dos veces.
     */
    /**
     * Ids que el worker puede tomar, bloqueados hasta el fin de la transacción.
     *
     * <p>Antes se seleccionaban las filas y se soltaba el bloqueo al terminar la
     * consulta, y el estado no cambiaba hasta que el hilo del pool empezaba a
     * ejecutar. La vuelta siguiente del worker, un segundo después, volvía a ver
     * la misma ejecución PENDIENTE y la lanzaba otra vez: dos notificaciones, dos
     * asignaciones. Ahora se seleccionan y se marcan en la misma transacción
     * ({@link #marcarReclamadas}). El LIMIT va antes de FOR UPDATE: con Pageable,
     * Spring lo añade al final y PostgreSQL rechaza la sentencia.
     */
    @Query(value = "SELECT e.id_workflow_execution FROM workflow_execution e "
            + "WHERE e.status_execution = 'PENDIENTE' "
            + "   OR (e.status_execution = 'EJECUTANDO' AND e.started_at_execution < :abandonadas "
            + "       AND e.attempts_execution <= :maxReintentos) "
            + "ORDER BY e.created_at_execution "
            + "LIMIT :limite "
            + "FOR UPDATE OF e SKIP LOCKED",
            nativeQuery = true)
    List<UUID> idsParaReclamar(@Param("abandonadas") OffsetDateTime abandonadas,
                               @Param("maxReintentos") int maxReintentos,
                               @Param("limite") int limite);

    @Modifying
    @Query(value = "UPDATE workflow_execution SET status_execution = 'EJECUTANDO', "
            + "started_at_execution = now(), attempts_execution = attempts_execution + 1 "
            + "WHERE id_workflow_execution IN (:ids)", nativeQuery = true)
    int marcarReclamadas(@Param("ids") Collection<UUID> ids);

    /**
     * Ejecuciones colgadas que ya agotaron sus reintentos: se dan por perdidas
     * con un error visible en vez de quedarse EJECUTANDO para siempre.
     */
    @Modifying
    @Query(value = "UPDATE workflow_execution SET status_execution = 'ERROR', finished_at_execution = now(), "
            + "error_message_execution = 'La ejecución quedó abandonada (el servidor se detuvo o el nodo "
            + "no terminó) y agotó sus reintentos.' "
            + "WHERE status_execution = 'EJECUTANDO' AND started_at_execution < :abandonadas "
            + "AND attempts_execution > :maxReintentos", nativeQuery = true)
    int cerrarAbandonadas(@Param("abandonadas") OffsetDateTime abandonadas,
                          @Param("maxReintentos") int maxReintentos);

    /** Creadas desde un instante, en cualquier estado: es lo que cuenta el límite por hora. */
    long countByWorkflowWorkflowAndCreatedAtExecutionAfter(UUID workflowId, OffsetDateTime desde);

    /** Terminadas antes de un instante, para la purga por retención. */
    @Query("SELECT e FROM WorkflowExecution e WHERE e.statusExecution IN :estados "
            + "AND e.createdAtExecution < :limite ORDER BY e.createdAtExecution")
    List<WorkflowExecution> terminadasAntesDe(@Param("estados") Collection<String> estados,
                                              @Param("limite") OffsetDateTime limite,
                                              Pageable pageable);

    /** Últimas ejecuciones de un workflow, para detectar uno que falla siempre. */
    List<WorkflowExecution> findTop5ByWorkflowWorkflowOrderByCreatedAtExecutionDesc(UUID workflowId);

    /**
     * Historial global, con filtros opcionales.
     *
     * <p>Se arma con una Specification y no con {@code (:x IS NULL OR ...)}:
     * PostgreSQL no sabe de qué tipo es un parámetro nulo comparado con NULL
     * ("no se pudo determinar el tipo del parámetro $1"), y Hibernate no admite
     * {@code cast(:x as UUID)}. Así solo entran en el SQL los filtros que vienen.
     */
    default Page<WorkflowExecution> buscar(UUID workflowId, String estado, String entidadTipo,
                                           UUID entidadId, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<WorkflowExecution> filtro = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> condiciones = new java.util.ArrayList<>();
            if (workflowId != null) condiciones.add(cb.equal(root.get("workflowWorkflow"), workflowId));
            if (estado != null) condiciones.add(cb.equal(root.get("statusExecution"), estado));
            if (entidadTipo != null) condiciones.add(cb.equal(root.get("entityTypeExecution"), entidadTipo));
            if (entidadId != null) condiciones.add(cb.equal(root.get("entityIdExecution"), entidadId));
            return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable orden = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAtExecution", "idWorkflowExecution"));
        return findAll(filtro, orden);
    }

    /**
     * Ejecuciones que llevan demasiado rato en EJECUTANDO.
     *
     * <p>Existe porque, si el proceso muere a mitad de una ejecución, nadie vuelve
     * a tocarla: se quedaría colgada para siempre y su ticket se quedaría a medias
     * sin que nadie lo detecte. Esta consulta las rescata.
     */
    @Query("SELECT e FROM WorkflowExecution e WHERE e.statusExecution = 'EJECUTANDO' "
            + "AND e.startedAtExecution IS NOT NULL AND e.startedAtExecution < :limite")
    List<WorkflowExecution> findAbandonadas(@Param("limite") OffsetDateTime limite);

    long countByStatusExecutionAndWorkflowWorkflow(String status, UUID workflowId);

/**
     * Historial con filtros, del más reciente al más antiguo. Mismo motivo que
     * {@link #buscar}: los filtros nulos no entran en el SQL.
     */
    default List<WorkflowExecution> consultarHistorial(UUID workflowId, String estado,
                                                       OffsetDateTime desde, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<WorkflowExecution> filtro = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> condiciones = new java.util.ArrayList<>();
            if (workflowId != null) condiciones.add(cb.equal(root.get("workflowWorkflow"), workflowId));
            if (estado != null) condiciones.add(cb.equal(root.get("statusExecution"), estado));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.get("createdAtExecution"), desde));
            return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable orden = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAtExecution"));
        return findAll(filtro, orden).getContent();
    }

    /**
     * Historial de un workflow, del más reciente al más antiguo.
     *
     * <p>Son dos métodos y no uno con un estado "que no sea null": con un solo
     * {@code OR}, pasar estado nulo devolvería el historial entero y la paginación
     * dejaría de tener sentido.
     */
    Page<WorkflowExecution> findByWorkflowWorkflowOrderByCreatedAtExecutionDesc(
            UUID workflowId, Pageable pageable);

    Page<WorkflowExecution> findByWorkflowWorkflowAndStatusExecutionOrderByCreatedAtExecutionDesc(
            UUID workflowId, String estado, Pageable pageable);

    /**
     * Ejecuciones en curso de un workflow concreto.
     *
     * <p>Lo usa el cupo: si un workflow ya tiene demasiadas en marcha, la nueva no
     * entra. Sin esto, un ticket que dispara diez veces en paralelo consumiría los
     * hilos del worker por un solo proceso.
     */
    long countByWorkflowWorkflowAndStatusExecutionIn(UUID workflowId, Collection<String> estados);
}
