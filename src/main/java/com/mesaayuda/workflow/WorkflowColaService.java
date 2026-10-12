package com.mesaayuda.workflow;

import com.mesaayuda.config.WorkflowProperties;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowWaitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Operaciones de cola del motor que tienen que ser atómicas.
 *
 * <p>Viven aparte del worker porque necesitan transacción, y el worker es un
 * {@code @Scheduled} que no la tiene: una llamada a un método {@code @Transactional}
 * desde la propia clase no pasaría por el proxy.
 */
@Service
public class WorkflowColaService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowColaService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowWaitRepository waitRepository;
    private final WorkflowProperties properties;

    public WorkflowColaService(WorkflowExecutionRepository executionRepository,
                               WorkflowWaitRepository waitRepository,
                               WorkflowProperties properties) {
        this.executionRepository = executionRepository;
        this.waitRepository = waitRepository;
        this.properties = properties;
    }

    /**
     * Toma hasta {@code capacidad} ejecuciones y las marca EJECUTANDO en la misma
     * transacción. Al confirmar, ninguna otra vuelta del worker (ni otra instancia
     * del backend) puede volver a tomarlas.
     */
    @Transactional
    public List<UUID> reclamarLote(int capacidad) {
        if (capacidad <= 0) {
            return List.of();
        }
        OffsetDateTime abandonadas = OffsetDateTime.now()
                .minusMinutes(properties.getMinutosEjecucionAbandonada());
        List<UUID> ids = executionRepository.idsParaReclamar(abandonadas, properties.getMaxReintentos(), capacidad);
        if (!ids.isEmpty()) {
            executionRepository.marcarReclamadas(ids);
        }
        return ids;
    }

    /** Cierra con error las ejecuciones colgadas que ya no se van a reintentar. */
    @Transactional
    public int cerrarAbandonadas() {
        OffsetDateTime abandonadas = OffsetDateTime.now()
                .minusMinutes(properties.getMinutosEjecucionAbandonada());
        int cerradas = executionRepository.cerrarAbandonadas(abandonadas, properties.getMaxReintentos());
        if (cerradas > 0) {
            log.warn("{} ejecución(es) de workflow abandonada(s) se cerraron con error", cerradas);
        }
        return cerradas;
    }

    /**
     * Devuelve a la cola las ejecuciones cuya espera venció.
     *
     * <p>La espera se cierra en la misma transacción, porque antes se quedaba
     * PENDING y el worker la volvía a encontrar cada segundo. La ejecución retoma
     * por el puerto que corresponde: NEXT tras un WAIT, VENCIDO tras una
     * aprobación que nadie decidió a tiempo.
     */
    @Transactional
    public int despertarVencidas() {
        List<WorkflowWait> vencidas = waitRepository.findVencidas(
                OffsetDateTime.now(), PageRequest.of(0, properties.getMaxEjecucionesConcurrentes()));
        int despertadas = 0;
        for (WorkflowWait espera : vencidas) {
            boolean esAprobacion = WorkflowWait.TIPO_APPROVAL.equals(espera.getKindWait());
            espera.setStatusWait(WorkflowWait.ESTADO_TIMEOUT);
            espera.setDecidedAtWait(OffsetDateTime.now());
            if (esAprobacion) {
                espera.setCommentWait("Nadie decidió antes de la fecha límite.");
            }
            waitRepository.save(espera);

            WorkflowExecution ejecucion = executionRepository.findById(espera.getExecutionExecution()).orElse(null);
            if (ejecucion == null || !ejecucion.estaDetenido()) {
                // Cancelada o terminada mientras esperaba: la espera se cierra igual
                // para que no vuelva a aparecer, pero no hay nada que reanudar.
                continue;
            }
            ejecucion.setStatusExecution(WorkflowExecution.ESTADO_PENDIENTE);
            boolean esReintento = WorkflowWait.MOTIVO_REINTENTO.equals(espera.getCommentWait());
            ejecucion.setResumePortExecution(esAprobacion ? WorkflowExecution.PUERTO_VENCIDO
                    : esReintento ? WorkflowExecution.PUERTO_REINTENTO : WorkflowEdgeDef.PUERTO_UNICO);
            executionRepository.save(ejecucion);
            despertadas++;
            log.info("La espera del nodo '{}' venció; la ejecución {} vuelve a la cola",
                    espera.getNodeIdWait(), ejecucion.getIdWorkflowExecution());
        }
        return despertadas;
    }
}
