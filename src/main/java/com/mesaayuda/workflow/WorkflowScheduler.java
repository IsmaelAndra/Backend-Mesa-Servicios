package com.mesaayuda.workflow;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mesaayuda.config.WorkflowProperties;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Lanza los workflows con disparador programado.
 *
 * <p>Cada vuelta mira los que ya tienen su próxima ejecución vencida, encola una
 * y calcula la siguiente a partir de AHORA, no de la que tocaba: si el servidor
 * estuvo parado un día, no se encolan de golpe las 24 pasadas que se perdió.
 *
 * <p>La clave del evento es el instante programado, así que dos instancias del
 * backend que lleguen a la vez encolan una sola ejecución (la deduplicación
 * rechaza la segunda).
 */
@Component
public class WorkflowScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowTriggerService triggerService;
    private final WorkflowProperties properties;

    public WorkflowScheduler(WorkflowRepository workflowRepository,
                             WorkflowTriggerService triggerService,
                             WorkflowProperties properties) {
        this.workflowRepository = workflowRepository;
        this.triggerService = triggerService;
        this.properties = properties;
    }

    /**
     * Sin transacción propia a propósito: cada encolado lleva la suya. Si una sola
     * envolviera el lote, un workflow que falla al encolar dejaría la
     * transacción marcada y se perderían también los demás.
     */
    @Scheduled(fixedDelayString = "${app.workflow.intervalo-programador:30000}")
    @SchedulerLock(name = "workflowProgramados", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void lanzarVencidos() {
        if (!properties.isEnabled()) {
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        List<Workflow> vencidos = workflowRepository.findProgramadosVencidos(ahora);
        for (Workflow w : vencidos) {
            OffsetDateTime tocaba = w.getNextRunAtWorkflow();
            try {
                String clave = "SCHEDULED:" + w.getIdWorkflow() + ":" + tocaba.toInstant().toEpochMilli();
                triggerService.encolar(w, new WorkflowEvento(WorkflowTriggerType.SCHEDULED.name(), clave,
                        Map.of("programadoPara", tocaba.toString()), null, null, null), 0, null);
            } catch (RuntimeException e) {
                log.error("No se pudo encolar el workflow programado '{}': {}", w.getNameWorkflow(),
                        e.getMessage(), e);
            }
            OffsetDateTime siguiente;
            try {
                siguiente = WorkflowProgramacion.siguiente(w.getTriggerConfig(), ahora);
            } catch (RuntimeException e) {
                // Configuración que dejó de ser válida: se detiene la programación en
                // lugar de reintentar cada 30 segundos.
                log.error("La programación del workflow '{}' no es válida y se detiene: {}",
                        w.getNameWorkflow(), e.getMessage());
                siguiente = null;
            }
            w.setNextRunAtWorkflow(siguiente);
            workflowRepository.save(w);
        }
    }
}
