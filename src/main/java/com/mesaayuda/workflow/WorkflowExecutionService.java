package com.mesaayuda.workflow;

import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowNodeRun;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowNodeRunRepository;
import com.mesaayuda.repository.WorkflowWaitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta y control de las ejecuciones.
 *
 * <p>Todo lo que responde a la pregunta "¿qué pasó con este proceso?". Sin esto,
 * un workflow que se para es un misterio: solo queda el mensaje de error del paso
 * concreto, y ni siquiera eso se ve desde la interfaz.
 *
 * <p><b>Cancelar no es lo mismo que borrar.</b> Una ejecución en curso se cancela:
 * el motor deja de correrla y queda registrada como CANCELADO, porque forma parte
 * del historial. Borrarla sería falselyar el pasado del proceso.
 */
@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final com.mesaayuda.repository.WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowNodeRunRepository nodeRunRepository;
    private final WorkflowWaitRepository waitRepository;
    private final com.mesaayuda.repository.WorkflowEventDedupRepository dedupRepository;
    private final com.mesaayuda.repository.WorkflowVersionRepository versionRepository;
    private final com.mesaayuda.repository.UserRepository userRepository;
    private final com.mesaayuda.repository.TicketRepository ticketRepository;

public WorkflowExecutionService(com.mesaayuda.repository.WorkflowRepository workflowRepository,
                                   WorkflowExecutionRepository executionRepository,
                                   WorkflowNodeRunRepository nodeRunRepository,
                                   WorkflowWaitRepository waitRepository,
                                   com.mesaayuda.repository.WorkflowEventDedupRepository dedupRepository,
                                   com.mesaayuda.repository.WorkflowVersionRepository versionRepository,
                                   com.mesaayuda.repository.UserRepository userRepository,
                                   com.mesaayuda.repository.TicketRepository ticketRepository) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.nodeRunRepository = nodeRunRepository;
        this.waitRepository = waitRepository;
        this.dedupRepository = dedupRepository;
        this.versionRepository = versionRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
    }

    /** Una ejecución, o 404 si no existe. */
    @Transactional(readOnly = true)
    public WorkflowExecution obtener(UUID ejecucionId) {
        return executionRepository.findById(ejecucionId)
                .orElseThrow(() -> new com.mesaayuda.exception.ResourceNotFoundException(
                        "Ejecución de workflow", "id", ejecucionId));
    }

    /** El nombre del workflow de una ejecución, para el listado y el detalle. */
    @Transactional(readOnly = true)
    public String nombreWorkflow(WorkflowExecution ejecucion) {
        return workflowRepository.findById(ejecucion.getWorkflowWorkflow())
                .map(w -> w.getNameWorkflow())
                .orElse("(workflow borrado)");
    }

    /**
     * Historial paginado de un workflow.
     *
     * <p>El filtro de estado es opcional a propósito: el caso normal es "muéstrame
     * todo", y quien busca un error concreto ya viene con el estado en la mano.
     * Son dos consultas distintas porque un estado nulo no se puede pasar a la
     * misma con un {@code OR}: eso devolvería la tabla entera.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<WorkflowExecution> consultar(
            UUID workflowId, String estado, org.springframework.data.domain.Pageable pageable) {
        if (estado == null || estado.isBlank()) {
            return executionRepository.findByWorkflowWorkflowOrderByCreatedAtExecutionDesc(
                    workflowId, pageable);
        }
        return executionRepository.findByWorkflowWorkflowAndStatusExecutionOrderByCreatedAtExecutionDesc(
                workflowId, estado.trim().toUpperCase(java.util.Locale.ROOT), pageable);
    }

    /** Pasos de una ejecución, en el orden en que se recorrieron. */
    @Transactional(readOnly = true)
    public List<WorkflowNodeRun> pasos(UUID ejecucionId) {
        return nodeRunRepository.findByExecutionExecutionOrderBySequenceNodeRunAsc(ejecucionId);
    }

    /** La espera pendiente de una ejecución, si está esperando. */
    @Transactional(readOnly = true)
    public Optional<WorkflowWait> esperaActual(UUID ejecucionId) {
        return waitRepository.findFirstByExecutionExecutionAndStatusWaitOrderByCreatedAtWaitAsc(
                ejecucionId, WorkflowWait.ESTADO_PENDIENTE);
    }

    /**
     * Cancela una ejecución.
     *
     * <p>Solo surte efecto sobre las que aún no terminaron. Una ejecución
     * COMPLETADA o en ERROR se queda como está: volver a abrirla para marcarla
     * cancelada falsearía el historial, que es justo lo que este historial
     * pretende evitar.
     */
    @Transactional
    public void cancelar(UUID ejecucionId) {
        WorkflowExecution ejecucion = executionRepository.findById(ejecucionId)
                .orElseThrow(() -> new com.mesaayuda.exception.ResourceNotFoundException(
                        "Ejecución de workflow", "id", ejecucionId));

        if (esTerminal(ejecucion.getStatusExecution())) {
            throw new BadRequestException("La ejecución " + ejecucionId + " ya terminó como "
                    + ejecucion.getStatusExecution() + " y no se puede cancelar.");
        }

        ejecucion.setStatusExecution(WorkflowExecution.ESTADO_CANCELADO);
        ejecucion.setFinishedAtExecution(OffsetDateTime.now());
        executionRepository.save(ejecucion);

        // Las esperas pendientes se cierran para que el worker no la recoja y la
        // vuelva a poner en cola. Si no, una ejecución cancelada en pleno proceso
        // reaparecería sola más adelante.
        List<WorkflowWait> esperas = waitRepository.findByExecutionExecutionOrderByCreatedAtWaitAsc(ejecucionId);
        for (WorkflowWait espera : esperas) {
            if (WorkflowWait.ESTADO_PENDIENTE.equals(espera.getStatusWait())) {
                espera.setStatusWait(WorkflowWait.ESTADO_RECHAZADA);
                espera.setCommentWait("Cancelada desde la interfaz.");
                espera.setDecidedAtWait(OffsetDateTime.now());
                waitRepository.save(espera);
            }
        }

        log.info("Ejecución {} cancelada; {} espera(s) cerrada(s)", ejecucionId,
                esperas.size());
    }

    /**
     * Purga las ejecuciones terminadas que ya están fuera de retención.
     *
     * <p>Sin esto las tablas del motor crecerían sin límite: nadie borra
     * ejecuciones por su cuenta, y son la parte más voluminosa del sistema.
     */
    @Transactional
    public int purgar(int diasRetencion) {
        if (diasRetencion <= 0) {
            // Un valor de cero significaría borrar el historial entero. Es
            // probablemente un error de configuración, no una intención.
            throw new BadRequestException("La retención debe ser de al menos un día.");
        }
        OffsetDateTime limite = OffsetDateTime.now().minusDays(diasRetencion);
        // Antes se reutilizaba consultarHistorial, que filtra por "creadas DESDE"
        // la fecha: la purga borraba las ejecuciones recientes y dejaba las viejas.
        // Y solo borraba los pasos, así que una ejecución con espera o con marca
        // de deduplicación hacía fallar la purga entera por clave foránea.
        List<WorkflowExecution> candidatas = executionRepository.terminadasAntesDe(
                List.of(WorkflowExecution.ESTADO_COMPLETADO, WorkflowExecution.ESTADO_ERROR,
                        WorkflowExecution.ESTADO_CANCELADO),
                limite, org.springframework.data.domain.PageRequest.of(0, 2000));
        int borradas = 0;
        for (WorkflowExecution ejecucion : candidatas) {
            UUID id = ejecucion.getIdWorkflowExecution();
            nodeRunRepository.deleteByExecutionExecution(id);
            waitRepository.borrarDeEjecucion(id);
            dedupRepository.borrarDeEjecucion(id);
            executionRepository.delete(ejecucion);
            borradas++;
        }
        log.info("Purgadas {} ejecuciones terminadas anteriores a {}", borradas, limite);
        return borradas;
    }

    /** Cuánto duró una ejecución, para el listado. */
    /**
     * La ejecución lista para el historial: con el número de versión (no el id de
     * la fila) y el nombre de quien la inició.
     */
    @Transactional(readOnly = true)
    public com.mesaayuda.dto.WorkflowExecutionResponse respuesta(WorkflowExecution e, String nombreWorkflow) {
        Integer numero = versionRepository.findById(e.getVersionWorkflow())
                .map(v -> v.getVersionWorkflow()).orElse(null);
        String iniciador = e.getStartedByExecution() == null ? null
                : userRepository.findById(e.getStartedByExecution())
                        .map(com.mesaayuda.service.NombreVisible::de).orElse(null);
        return com.mesaayuda.dto.WorkflowExecutionResponse.from(e, nombreWorkflow, numero, iniciador,
                etiquetaEntidad(e));
    }

    /**
     * Cómo se nombra la entidad en el historial: "#43" para un ticket y el nombre
     * para un usuario. El uuid sirve para enlazar, no para leerlo.
     */
    private String etiquetaEntidad(WorkflowExecution e) {
        if (e.getEntityIdExecution() == null) {
            return null;
        }
        if ("TICKET".equals(e.getEntityTypeExecution())) {
            return ticketRepository.findById(e.getEntityIdExecution())
                    .map(t -> "#" + t.getNumberTicket()).orElse(null);
        }
        if ("USUARIO".equals(e.getEntityTypeExecution())) {
            return userRepository.findById(e.getEntityIdExecution())
                    .map(com.mesaayuda.service.NombreVisible::de).orElse(null);
        }
        return null;
    }

    /** Historial global con filtros opcionales (workflow, estado, entidad). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<WorkflowExecution> buscar(
            UUID workflowId, String estado, String entidadTipo, UUID entidadId,
            org.springframework.data.domain.Pageable pageable) {
        return executionRepository.buscar(workflowId,
                estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(java.util.Locale.ROOT),
                entidadTipo == null || entidadTipo.isBlank() ? null : entidadTipo.trim().toUpperCase(java.util.Locale.ROOT),
                entidadId, pageable);
    }

    public static Duration duracion(WorkflowExecution ejecucion) {
        if (ejecucion.getStartedAtExecution() == null || ejecucion.getFinishedAtExecution() == null) {
            return Duration.ZERO;
        }
        return Duration.between(ejecucion.getStartedAtExecution(), ejecucion.getFinishedAtExecution());
    }

    private boolean esTerminal(String estado) {
        return WorkflowExecution.ESTADO_COMPLETADO.equals(estado)
                || WorkflowExecution.ESTADO_ERROR.equals(estado)
                || WorkflowExecution.ESTADO_CANCELADO.equals(estado);
    }
}