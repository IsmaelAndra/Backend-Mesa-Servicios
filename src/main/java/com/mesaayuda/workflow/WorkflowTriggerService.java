package com.mesaayuda.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.config.WorkflowProperties;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.WorkflowEventDedupRepository;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Convierte un hecho del sistema en ejecuciones pendientes.
 *
 * <p>No ejecuta nada: crea la fila PENDIENTE y el worker la recoge. Aquí viven
 * las defensas contra cascadas:
 * <ul>
 *   <li><b>Profundidad.</b> Un evento provocado por una ejecución hereda su
 *       profundidad + 1. Antes todas empezaban en 0, así que el tope de
 *       profundidad no cortaba nada.</li>
 *   <li><b>Sin autodisparo.</b> Las acciones de un workflow no vuelven a disparar
 *       ese mismo workflow: "cuando se actualice el ticket, cambia su prioridad"
 *       sería un bucle inmediato.</li>
 *   <li><b>Cupo por hora y simultáneas</b> por workflow.</li>
 *   <li><b>Deduplicación</b> del mismo hecho entregado dos veces.</li>
 * </ul>
 *
 * <p>{@code disparar} va en {@code REQUIRES_NEW} porque se llama desde el
 * afterCommit de otra transacción, que ya se está cerrando: un REQUIRED se
 * engancharía a ella y el encolado quedaría sin transacción.
 */
@Service
public class WorkflowTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowEventDedupRepository dedupRepository;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public WorkflowTriggerService(WorkflowRepository workflowRepository,
                                  WorkflowVersionRepository versionRepository,
                                  WorkflowExecutionRepository executionRepository,
                                  WorkflowEventDedupRepository dedupRepository,
                                  WorkflowProperties properties,
                                  ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.dedupRepository = dedupRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Compatibilidad: evento sin entidad ni origen. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int disparar(String tipoDisparador, String claveEvento, Map<String, Object> payload) {
        return disparar(new WorkflowEvento(tipoDisparador, claveEvento, payload, null, null, null));
    }

    /** Encola una ejecución por cada workflow activo con este disparador. Devuelve cuántas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int disparar(WorkflowEvento evento) {
        if (!properties.isEnabled()) {
            log.debug("El motor está apagado; se ignora el evento {}", evento.tipo());
            return 0;
        }
        if (evento.tipo() == null || evento.tipo().isBlank() || evento.clave() == null) {
            return 0;
        }
        int profundidad = evento.origen() == null ? 0 : evento.origen().profundidad() + 1;
        if (profundidad > properties.getMaxProfundidad()) {
            log.warn("El evento {} lo provocó la ejecución {} con profundidad {}; se descarta para "
                            + "cortar la cascada (máximo {}).", evento.tipo(), evento.origen().ejecucionId(),
                    profundidad, properties.getMaxProfundidad());
            return 0;
        }
        List<Workflow> candidatos = workflowRepository.findActivosPorTrigger(
                evento.tipo().trim().toUpperCase(Locale.ROOT));
        int creadas = 0;
        for (Workflow workflow : candidatos) {
            if (evento.origen() != null && workflow.getIdWorkflow().equals(evento.origen().workflowId())) {
                log.debug("El workflow '{}' provocó el evento {}; no se dispara a sí mismo.",
                        workflow.getNameWorkflow(), evento.tipo());
                continue;
            }
            if (encolar(workflow, evento, profundidad, null) != null) {
                creadas++;
            }
        }
        if (creadas > 0) {
            log.info("El evento {} ({}) encoló {} ejecuciones", evento.tipo(), evento.clave(), creadas);
        }
        return creadas;
    }

    /**
     * Crea la ejecución pendiente. Devuelve null si no se creó (sin versión, sin
     * cupo o evento duplicado).
     */
    @Transactional
    public WorkflowExecution encolar(Workflow workflow, WorkflowEvento evento, int profundidad, UUID iniciadoPor) {
        WorkflowVersion version = versionRepository
                .findByWorkflowWorkflowAndIsActiveVersionTrue(workflow.getIdWorkflow())
                .orElse(null);
        if (version == null) {
            log.warn("El workflow '{}' está activo pero no tiene versión publicada. Se ignora el evento {}.",
                    workflow.getNameWorkflow(), evento.clave());
            return null;
        }
        if (!tieneCupo(workflow)) {
            log.warn("El workflow '{}' alcanzó su límite de ejecuciones. Se ignora el evento {}.",
                    workflow.getNameWorkflow(), evento.clave());
            return null;
        }

        Map<String, Object> datos = evento.datos() == null ? Map.of() : evento.datos();
        // Las claves planas (ticketId, prioridad...) siguen en la raíz por
        // compatibilidad con los grafos ya publicados; evento.* es la forma nueva.
        Map<String, Object> variables = new LinkedHashMap<>(datos);
        variables.put("evento", new LinkedHashMap<>(datos));
        variables.put("disparador", evento.tipo());

        WorkflowExecution ejecucion = new WorkflowExecution();
        ejecucion.setWorkflowWorkflow(workflow.getIdWorkflow());
        ejecucion.setVersionWorkflow(version.getIdWorkflowVersion());
        ejecucion.setTriggerType(evento.tipo());
        ejecucion.setCorrelationKey(recortar(evento.clave(), 120));
        ejecucion.setStatusExecution(WorkflowExecution.ESTADO_PENDIENTE);
        ejecucion.setVariablesExecution(json(variables));
        ejecucion.setTriggerPayload(json(datos));
        ejecucion.setDepthExecution(profundidad);
        ejecucion.setAttemptsExecution(0);
        // Quien la inició: la persona en una ejecución manual; el autor del
        // workflow cuando la dispara un evento.
        ejecucion.setStartedByExecution(iniciadoPor != null ? iniciadoPor : workflow.getCreatedByWorkflow());
        ejecucion.setEntityTypeExecution(evento.entidadTipo());
        ejecucion.setEntityIdExecution(evento.entidadId());
        ejecucion.setOriginExecution(evento.origen() == null ? null : evento.origen().ejecucionId());
        ejecucion.setCreatedAtExecution(OffsetDateTime.now());
        ejecucion = executionRepository.save(ejecucion);

        // La reserva va DESPUÉS de crear la ejecución, porque la clave la referencia.
        // Si vuelve 0 filas, otro proceso ganó la carrera con el mismo hecho.
        int reservada = dedupRepository.reservarEvento(workflow.getIdWorkflow(), ejecucion.getCorrelationKey(),
                ejecucion.getIdWorkflowExecution());
        if (reservada == 0) {
            executionRepository.delete(ejecucion);
            log.debug("El evento {} ya se había procesado para el workflow '{}'; se descarta el duplicado.",
                    evento.clave(), workflow.getNameWorkflow());
            return null;
        }
        return ejecucion;
    }

    /**
     * Ejecución manual. Se puede pasar {@code ticketId} o {@code usuarioId} en el
     * payload para que las variables vivas apunten a esa entidad.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowExecution ejecutarManualmente(Workflow workflow, Map<String, Object> payload, UUID iniciadoPor) {
        versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(workflow.getIdWorkflow())
                .orElseThrow(() -> new BadRequestException("El workflow '" + workflow.getNameWorkflow()
                        + "' no tiene ninguna versión publicada. Publíquela antes de ejecutarlo."));
        if (!tieneCupo(workflow)) {
            throw new BadRequestException("El workflow '" + workflow.getNameWorkflow()
                    + "' alcanzó su límite de ejecuciones. Espere un momento.");
        }
        Map<String, Object> datos = payload == null ? Map.of() : payload;
        String entidadTipo = null;
        UUID entidadId = null;
        if (uuid(datos.get("ticketId")) != null) {
            entidadTipo = "TICKET";
            entidadId = uuid(datos.get("ticketId"));
        } else if (uuid(datos.get("usuarioId")) != null) {
            entidadTipo = "USUARIO";
            entidadId = uuid(datos.get("usuarioId"));
        }
        String clave = "MANUAL:" + workflow.getIdWorkflow() + ":" + System.currentTimeMillis()
                + ":" + java.util.UUID.randomUUID().toString().substring(0, 8);
        WorkflowExecution ejecucion = encolar(workflow,
                new WorkflowEvento(WorkflowTriggerType.MANUAL.name(), clave, datos, entidadTipo, entidadId, null),
                0, iniciadoPor);
        if (ejecucion == null) {
            throw new BadRequestException("No se pudo encolar la ejecución manual.");
        }
        log.info("Ejecución manual {} encolada para el workflow '{}' por el usuario {}",
                ejecucion.getIdWorkflowExecution(), workflow.getNameWorkflow(), iniciadoPor);
        return ejecucion;
    }

    /**
     * Si el workflow puede encolar otra ejecución.
     *
     * <p>El cupo por hora cuenta todas las creadas en la última hora. Antes solo
     * contaba las que seguían PENDIENTES, y como el worker las toma en un
     * segundo, el límite no limitaba nada.
     */
    private boolean tieneCupo(Workflow workflow) {
        long enCurso = executionRepository.countByStatusExecutionAndWorkflowWorkflow(
                WorkflowExecution.ESTADO_EJECUTANDO, workflow.getIdWorkflow());
        if (enCurso >= properties.getMaxEjecucionesSimultaneas()) {
            return false;
        }
        long ultimaHora = executionRepository.countByWorkflowWorkflowAndCreatedAtExecutionAfter(
                workflow.getIdWorkflow(), OffsetDateTime.now().minusHours(1));
        return ultimaHora < properties.getMaxEjecucionesPorHora();
    }

    /** Purga las marcas de deduplicación caducadas. La llama el worker cada minuto. */
    @Transactional
    public int limpiarDeduplicacion() {
        OffsetDateTime limite = OffsetDateTime.now().minusMinutes(properties.getMinutosDeduplicacion());
        int borradas = dedupRepository.borrarAntiguos(limite);
        if (borradas > 0) {
            log.debug("Se purgaron {} claves de deduplicación caducadas", borradas);
        }
        return borradas;
    }

    private static UUID uuid(Object valor) {
        if (valor instanceof UUID u) {
            return u;
        }
        if (valor != null) {
            try {
                return UUID.fromString(String.valueOf(valor).trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private String recortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max);
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor == null ? Map.of() : valor);
        } catch (Exception e) {
            log.warn("No se pudo serializar el payload del disparador: {}", e.getMessage());
            return "{}";
        }
    }
}
