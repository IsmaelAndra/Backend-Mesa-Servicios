package com.mesaayuda.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.config.WorkflowProperties;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowNodeRun;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowNodeRunRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import com.mesaayuda.service.SystemAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Recorre un grafo de workflow y ejecuta sus nodos.
 *
 * <p>El motor es un intérprete, no un compilador: lee la versión vigente, camina el
 * grafo y ejecuta de uno en uno. No compila nada ni evalúa expresiones: lo que
 * está guardado es lo que se ejecuta, y lo que se ejecuta se puede leer.
 *
 * <p><b>Una transacción por nodo.</b> Antes todo el recorrido iba en una sola. Las
 * acciones llaman a servicios {@code @Transactional} (TicketService, SlaService,
 * TaskService) que se unían a ella; si uno lanzaba, la marcaba para revertir, y al
 * final se revertía TODO: los pasos que habían funcionado, el rastro y hasta el
 * propio registro del error. La ejecución fallaba en silencio, que es justo lo que
 * un motor de automatizaciones no puede hacer. Ahora cada nodo confirma o revierte
 * lo suyo, y el rastro y el estado se guardan siempre aparte.
 *
 * <p><b>Reanudar es seguir, no repetir.</b> Una ejecución que vuelve de un WAIT o de
 * una APPROVAL continúa por el conector del puerto que corresponde (NEXT,
 * APROBADO, RECHAZADO, VENCIDO). Antes volvía a ejecutar el propio nodo de pausa,
 * que pausaba otra vez: la ejecución no salía nunca de la espera.
 *
 * <p>El motor no invoca nada directamente: todo pasa por {@link WorkflowActor}, que
 * ejecuta cada acción con la autoridad del autor del workflow.
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    /** Fallos seguidos a partir de los cuales el workflow se marca en ERROR y deja de dispararse. */
    static final int FALLOS_SEGUIDOS_PARA_ERROR = 5;

    /**
     * Puertos de reanudación que, si no tienen conector, terminan la ejecución en
     * lugar de caer al conector único. Sin esto, un rechazo sin rama "RECHAZADO"
     * seguiría por la salida normal como si lo hubieran aprobado.
     */
    private static final Set<String> PUERTOS_SIN_ALTERNATIVA = Set.of(
            WorkflowExecution.PUERTO_RECHAZADO, WorkflowExecution.PUERTO_VENCIDO);

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowNodeRunRepository nodeRunRepository;
    private final WorkflowNodeRegistry registry;
    private final WorkflowActor actor;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaccion;
    private final UserRepository userRepository;
    private final WorkflowVariables variables;
    private final SystemAuditService systemAuditService;
    private final com.mesaayuda.repository.WorkflowWaitRepository waitRepository;
    private final WorkflowEnVivo enVivo;

    public WorkflowEngine(WorkflowRepository workflowRepository,
                          WorkflowVersionRepository versionRepository,
                          WorkflowExecutionRepository executionRepository,
                          WorkflowNodeRunRepository nodeRunRepository,
                          WorkflowNodeRegistry registry,
                          WorkflowActor actor,
                          WorkflowProperties properties,
                          ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager,
                          UserRepository userRepository,
                          WorkflowVariables variables,
                          SystemAuditService systemAuditService,
                          com.mesaayuda.repository.WorkflowWaitRepository waitRepository,
                          WorkflowEnVivo enVivo) {
        this.workflowRepository = workflowRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.nodeRunRepository = nodeRunRepository;
        this.registry = registry;
        this.actor = actor;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.userRepository = userRepository;
        this.variables = variables;
        this.systemAuditService = systemAuditService;
        this.waitRepository = waitRepository;
        this.enVivo = enVivo;
    }

    /** Lo necesario para recorrer, leído en una transacción corta al empezar. */
    private record Carga(WorkflowExecution ejecucion, Workflow workflow, WorkflowVersion version,
                         WorkflowGraphDef grafo, WorkflowContext contexto) {
    }

    /**
     * Ejecuta una ejecución hasta que termine, se detenga o falle.
     *
     * <p>No es transaccional a propósito: cada nodo abre la suya. Ver la nota de la clase.
     */
    public void ejecutar(UUID executionId) {
        Carga carga = transaccion.execute(status -> cargar(executionId));
        if (carga == null) {
            return;
        }
        log.info("Ejecutando {} del workflow '{}' (versión {}) desde el nodo '{}'",
                executionId, carga.workflow().getNameWorkflow(),
                carga.version().getVersionWorkflow(), carga.contexto().getCurrentNodeId());
        recorrer(carga);
    }

    /** Prepara la ejecución. Devuelve null si no hay nada que recorrer (ya terminó o falló al cargar). */
    private Carga cargar(UUID executionId) {
        WorkflowExecution ejecucion = executionRepository.findById(executionId).orElse(null);
        if (ejecucion == null) {
            log.warn("La ejecución {} ya no existe. No hay nada que ejecutar.", executionId);
            return null;
        }
        if (ejecucion.termino()) {
            return null;
        }
        if (ejecucion.getDepthExecution() != null
                && ejecucion.getDepthExecution() > properties.getMaxProfundidad()) {
            // A dispara B dispara C... Con dos procesos mal configurados que se
            // alimentan entre sí, esto para la cascada antes de que sature.
            finalizarError(ejecucion, null,
                    "Se superó la profundidad máxima de workflows encadenados ("
                            + properties.getMaxProfundidad() + "). Revisa si dos workflows se disparan mutuamente.");
            return null;
        }

        Workflow workflow = workflowRepository.findById(ejecucion.getWorkflowWorkflow()).orElse(null);
        if (workflow == null || Boolean.TRUE.equals(workflow.getDeletedWorkflow())) {
            finalizarError(ejecucion, null, "El workflow " + ejecucion.getWorkflowWorkflow() + " ya no existe.");
            return null;
        }

        // El motor actúa con la autoridad del autor. Si el autor ya no está activo
        // (lo dieron de baja, lo desactivaron), su automatización no puede seguir
        // actuando en su nombre: sería una puerta trasera que sobrevive a la baja.
        String autorInvalido = autorNoValido(workflow);
        if (autorInvalido != null) {
            finalizarError(ejecucion, null, autorInvalido);
            marcarWorkflowEnError(workflow, autorInvalido);
            return null;
        }

        WorkflowVersion version = versionRepository.findById(ejecucion.getVersionWorkflow()).orElse(null);
        if (version == null) {
            finalizarError(ejecucion, null, "No se encuentra la versión con la que arrancó la ejecución. "
                    + "Las versiones publicadas no se borran; revisa si se modificó la base.");
            return null;
        }

        WorkflowGraphDef grafo;
        try {
            grafo = objectMapper.readValue(version.getDefinitionWorkflow(), WorkflowGraphDef.class);
        } catch (Exception e) {
            log.error("Definición ilegible en la versión {} del workflow {}", version.getVersionWorkflow(),
                    workflow.getIdWorkflow(), e);
            finalizarError(ejecucion, null, "La versión " + version.getVersionWorkflow()
                    + " tiene una definición ilegible.");
            return null;
        }

        WorkflowContext contexto = new WorkflowContext(
                ejecucion.getIdWorkflowExecution(),
                workflow.getIdWorkflow(),
                version.getIdWorkflowVersion(),
                ejecucion.getCorrelationKey(),
                ejecucion.getDepthExecution() == null ? 0 : ejecucion.getDepthExecution());
        contexto.cargarVariables(leerVariables(ejecucion));
        variables.vincular(contexto);
        // El ámbito viaja en el contexto porque los nodos lo necesitan para
        // decidir (una aprobación no puede pedir más permisos que el proceso).
        contexto.escribir("authorityScope", workflow.getAuthorityScope());
        contexto.escribir("triggerPayload", ejecucion.getTriggerPayload());
        contexto.escribir("ejecucion.id", ejecucion.getIdWorkflowExecution());
        contexto.escribir("ejecucion.version", version.getVersionWorkflow());
        contexto.escribir("ejecucion.workflow", workflow.getNameWorkflow());
        contexto.escribir("ejecucion.iniciadoPor", ejecucion.getStartedByExecution());

        // Tras una aprobación, el resultado queda en nodos.<id>.decision también
        // cuando venció sin decidir (la decisión manual ya lo escribe al decidir).
        String puertoReanudar = ejecucion.getResumePortExecution();
        String nodoPausa = ejecucion.getCurrentNodeExecution();
        if (puertoReanudar != null && nodoPausa != null
                && contexto.leerGuardada("nodos." + nodoPausa + ".decision") == null
                && !WorkflowEdgeDef.PUERTO_UNICO.equals(puertoReanudar)
                && !WorkflowExecution.PUERTO_REINTENTO.equals(puertoReanudar)) {
            contexto.escribir("nodos." + nodoPausa + ".decision", puertoReanudar);
        }

        String inicio = puntoDePartida(ejecucion, grafo);
        if (inicio == null) {
            if (ejecucion.getResumePortExecution() != null) {
                // Volvió de una pausa por un puerto sin conector (un rechazo sin rama
                // RECHAZADO, una aprobación vencida sin rama VENCIDO): termina ahí.
                String motivo = "Retomada por " + ejecucion.getResumePortExecution()
                        + " sin rama para ese resultado: la ejecución termina.";
                ejecucion.setResumePortExecution(null);
                guardarVariables(ejecucion, contexto);
                finalizar(ejecucion, workflow, motivo);
            } else {
                finalizarError(ejecucion, null, "El grafo no tiene nodo de inicio.");
            }
            return null;
        }
        contexto.setCurrentNodeId(inicio);
        ejecucion.setResumePortExecution(null);
        ejecucion.setStatusExecution(WorkflowExecution.ESTADO_EJECUTANDO);
        if (ejecucion.getStartedAtExecution() == null) {
            ejecucion.setStartedAtExecution(OffsetDateTime.now());
        }
        executionRepository.save(ejecucion);
        return new Carga(ejecucion, workflow, version, grafo, contexto);
    }

    /**
     * Nodo por el que empieza esta vuelta.
     *
     * <ul>
     *   <li>Vuelve de una pausa: el siguiente al nodo de pausa por el puerto de reanudación.</li>
     *   <li>Tiene nodo actual sin puerto: es una ejecución que se cortó a medias
     *       (reinicio del servidor) y se retoma en el nodo que no llegó a terminar.</li>
     *   <li>Si no, el nodo de inicio.</li>
     * </ul>
     */
    private String puntoDePartida(WorkflowExecution ejecucion, WorkflowGraphDef grafo) {
        String actual = ejecucion.getCurrentNodeExecution();
        String puerto = ejecucion.getResumePortExecution();
        if (actual != null && !actual.isBlank()) {
            if (puerto == null || puerto.isBlank() || WorkflowExecution.PUERTO_REINTENTO.equals(puerto)) {
                // Sin puerto: se cortó a medias. REINTENTO: el nodo falló por una
                // causa pasajera y toca volver a ejecutarlo.
                return actual;
            }
            return siguiente(grafo, actual, puerto);
        }
        WorkflowNodeDef inicio = grafo.nodoInicio();
        return inicio == null ? null : inicio.id();
    }

    /** Bucle principal: un nodo por vuelta, con tope de pasos. */
    private void recorrer(Carga carga) {
        WorkflowGraphDef grafo = carga.grafo();
        WorkflowContext contexto = carga.contexto();
        Workflow workflow = carga.workflow();
        WorkflowExecution ejecucion = carga.ejecucion();

        while (true) {
            if (fueCancelada(ejecucion)) {
                log.info("La ejecución {} se canceló mientras corría; se detiene antes del nodo '{}'",
                        ejecucion.getIdWorkflowExecution(), contexto.getCurrentNodeId());
                return;
            }
            if (contexto.getPasos() >= properties.getMaxPasos()) {
                cerrarConError(ejecucion, workflow, contexto.getCurrentNodeId(),
                        "Se superó el límite de " + properties.getMaxPasos() + " pasos. Probablemente hay un bucle.");
                emitirFin(ejecucion);
                return;
            }
            WorkflowNodeDef nodo = nodo(grafo, contexto.getCurrentNodeId());
            if (nodo == null) {
                cerrarConError(ejecucion, workflow, contexto.getCurrentNodeId(),
                        "El nodo '" + contexto.getCurrentNodeId() + "' no existe en la versión publicada.");
                emitirFin(ejecucion);
                return;
            }

            contexto.sumarPaso();
            int secuencia = siguienteSecuencia(ejecucion, contexto);
            Map<String, Object> antes = copia(contexto.variables());
            OffsetDateTime inicio = OffsetDateTime.now();
            emitir(ejecucion, WorkflowEnVivo.NODO_INICIO, nodo, secuencia,
                    WorkflowNodeRun.ESTADO_EJECUTANDO, null, null, null, null, null);
            WorkflowResult resultado = ejecutarNodo(workflow, contexto, nodo);
            OffsetDateTime fin = OffsetDateTime.now();

            if (resultado.esFallo()) {
                // Lo que el nodo escribió en las variables se descarta: sus cambios
                // en la base ya se revirtieron con su transacción.
                contexto.cargarVariables(antes);
                if (resultado.esReintentable() && programarReintento(ejecucion, contexto, nodo, resultado,
                        secuencia, inicio, fin)) {
                    emitir(ejecucion, WorkflowEnVivo.NODO_FIN, nodo, secuencia, WorkflowNodeRun.ESTADO_ERROR,
                            null, null, resultado.getMotivo(), null, duracion(inicio, fin));
                    emitirFin(ejecucion);
                    return;
                }
            }
            String siguiente = resultado.esFallo() || !resultado.continua()
                    ? null : siguiente(grafo, nodo.id(), resultado.getPuerto());
            guardarPaso(ejecucion, workflow, contexto, nodo, resultado, secuencia, inicio, fin,
                    antes, siguiente);
            emitir(ejecucion, WorkflowEnVivo.NODO_FIN, nodo, secuencia,
                    resultado.esFallo() ? WorkflowNodeRun.ESTADO_ERROR : WorkflowNodeRun.ESTADO_COMPLETADO,
                    resultado.getPuerto(), siguiente, resultado.getMotivo(),
                    resultado.esFallo() ? null : salida(antes, contexto.variables()), duracion(inicio, fin));

            if (resultado.esFallo() || !resultado.continua()) {
                emitirFin(ejecucion);
                return;
            }
            if (siguiente == null) {
                // Un nodo sin salida que no es FIN: el validador no lo deja publicar,
                // así que solo pasa con grafos viejos. Se da por terminado.
                log.warn("El nodo '{}' no tiene conector de salida. La ejecución {} termina aquí.",
                        nodo.id(), ejecucion.getIdWorkflowExecution());
                transaccion.executeWithoutResult(s -> finalizar(ejecucion, workflow, null));
                emitirFin(ejecucion);
                return;
            }
            contexto.setCurrentNodeId(siguiente);
        }
    }

    /**
     * Ejecuta un nodo en su propia transacción, con la autoridad del autor y
     * dejando marcada la ejecución de origen para los eventos que provoque.
     */
    private WorkflowResult ejecutarNodo(Workflow workflow, WorkflowContext contexto, WorkflowNodeDef nodo) {
        WorkflowNodeHandler handler = registry.handler(WorkflowNodeType.desde(nodo.type()));
        if (handler == null) {
            return WorkflowResult.fallar("El motor no sabe ejecutar nodos de tipo " + nodo.type() + ".");
        }
        try {
            WorkflowResult r = transaccion.execute(status -> {
                WorkflowOrigen.establecer(contexto.getExecutionId(), workflow.getIdWorkflow(), contexto.getDepth());
                try {
                    WorkflowResult res = actor.ejecutarComo(workflow.getIdWorkflow(),
                            workflow.getAuthorityScope(), workflow.getCreatedByWorkflow(),
                            () -> handler.ejecutar(contexto, nodo));
                    if (res == null) {
                        res = WorkflowResult.fallar("El nodo '" + nodo.id() + "' no devolvió resultado.");
                    }
                    if (res.esFallo()) {
                        // Revertir lo que el nodo hizo. Marcarlo aquí, en local, evita
                        // además que un servicio que ya la marcó haga saltar
                        // UnexpectedRollbackException y se pierda el motivo real.
                        status.setRollbackOnly();
                    }
                    return res;
                } finally {
                    WorkflowOrigen.limpiar();
                }
            });
            return r == null ? WorkflowResult.fallar("El nodo '" + nodo.id() + "' no devolvió resultado.") : r;
        } catch (RuntimeException e) {
            log.error("El nodo '{}' ({}) de la ejecución {} lanzó una excepción",
                    nodo.id(), nodo.type(), contexto.getExecutionId(), e);
            return WorkflowResult.fallar(describir(e));
        }
    }

    /**
     * Fallo pasajero (IA saturada, timeout de Graph): en vez de dar la ejecución
     * por perdida, se deja en espera y se vuelve a ejecutar el mismo nodo más
     * tarde, con espera creciente (1, 2, 4... minutos). Usa el mismo mecanismo que
     * un WAIT: ningún hilo se queda dormido. Devuelve false si ya agotó los
     * reintentos y hay que tratarlo como un error normal.
     */
    private boolean programarReintento(WorkflowExecution ejecucion, WorkflowContext contexto, WorkflowNodeDef nodo,
                                       WorkflowResult resultado, int secuencia,
                                       OffsetDateTime inicio, OffsetDateTime fin) {
        String clave = "reintentos." + nodo.id();
        Object previo = contexto.leerGuardada(clave);
        int hechos = previo instanceof Number n ? n.intValue() : 0;
        if (hechos >= properties.getMaxReintentosNodo()) {
            return false;
        }
        int minutos = 1 << hechos;
        OffsetDateTime cuando = OffsetDateTime.now().plusMinutes(minutos);
        String motivo = resultado.getMotivo() + " Se reintenta (" + (hechos + 1) + " de "
                + properties.getMaxReintentosNodo() + ") en " + minutos + " min.";
        transaccion.executeWithoutResult(status -> {
            contexto.escribir(clave, hechos + 1);
            com.mesaayuda.entity.WorkflowWait espera = new com.mesaayuda.entity.WorkflowWait();
            espera.setExecutionExecution(ejecucion.getIdWorkflowExecution());
            espera.setNodeIdWait(nodo.id());
            espera.setKindWait(com.mesaayuda.entity.WorkflowWait.TIPO_DELAY);
            espera.setResumeAtWait(cuando);
            espera.setStatusWait(com.mesaayuda.entity.WorkflowWait.ESTADO_PENDIENTE);
            espera.setCommentWait(com.mesaayuda.entity.WorkflowWait.MOTIVO_REINTENTO);
            espera.setCreatedAtWait(OffsetDateTime.now());
            waitRepository.save(espera);

            WorkflowNodeRun run = new WorkflowNodeRun();
            run.setExecutionExecution(ejecucion.getIdWorkflowExecution());
            run.setNodeIdNodeRun(nodo.id());
            run.setNodeTypeNodeRun(nodo.type());
            run.setSequenceNodeRun(secuencia);
            run.setStatusNodeRun(WorkflowNodeRun.ESTADO_ERROR);
            run.setErrorMessageNodeRun(recortar(motivo, 1000));
            run.setInputNodeRun(json(entrada(nodo, resultado)));
            run.setOutputNodeRun("{}");
            run.setStartedAtNodeRun(inicio);
            run.setFinishedAtNodeRun(fin);
            run.setCreatedAtNodeRun(OffsetDateTime.now());
            nodeRunRepository.save(run);

            guardarVariables(ejecucion, contexto);
            ejecucion.setStatusExecution(WorkflowExecution.ESTADO_ESPERANDO);
            ejecucion.setCurrentNodeExecution(nodo.id());
            ejecucion.setResultExecution(json(Map.of("motivo", motivo)));
            executionRepository.save(ejecucion);
        });
        log.warn("Ejecución {}: el nodo '{}' falló por una causa pasajera; reintento {} en {} min",
                ejecucion.getIdWorkflowExecution(), nodo.id(), hechos + 1, minutos);
        return true;
    }

    /** Si alguien la canceló desde la interfaz mientras el motor la recorría. */
    private boolean fueCancelada(WorkflowExecution ejecucion) {
        return executionRepository.findById(ejecucion.getIdWorkflowExecution())
                .map(e -> WorkflowExecution.ESTADO_CANCELADO.equals(e.getStatusExecution()))
                .orElse(true);
    }

    /** Siguiente nodo desde el puerto elegido. */
    private String siguiente(WorkflowGraphDef grafo, String nodoId, String puerto) {
        String p = puerto == null || puerto.isBlank() ? WorkflowEdgeDef.PUERTO_UNICO : puerto;
        List<WorkflowEdgeDef> candidatas = grafo.salidasDe(nodoId, p);
        if (candidatas.isEmpty() && !WorkflowEdgeDef.PUERTO_UNICO.equals(p)
                && !PUERTOS_SIN_ALTERNATIVA.contains(p)) {
            // El nodo eligió un puerto que no está conectado. Se busca por el
            // único, que suele ser lo que quería el constructor.
            candidatas = grafo.salidasDe(nodoId, WorkflowEdgeDef.PUERTO_UNICO);
        }
        return candidatas.isEmpty() ? null : candidatas.get(0).to();
    }

    private WorkflowNodeDef nodo(WorkflowGraphDef grafo, String id) {
        return grafo.nodos().stream().filter(n -> Objects.equals(id, n.id())).findFirst().orElse(null);
    }

    // ============================ En vivo ============================

    /** Manda el paso a quien sigue la ejecución en el lienzo. No falla nunca. */
    private void emitir(WorkflowExecution ejecucion, String tipo, WorkflowNodeDef nodo, Integer secuencia,
                        String estado, String puerto, String siguiente, String motivo,
                        Map<String, Object> salida, Long duracionMs) {
        if (enVivo == null) {
            return;
        }
        enVivo.emitir(ejecucion.getStartedByExecution(), new WorkflowEnVivo.Paso(
                ejecucion.getIdWorkflowExecution(), ejecucion.getWorkflowWorkflow(), tipo,
                nodo == null ? null : nodo.id(), nodo == null ? null : nodo.type(), secuencia, estado,
                puerto, siguiente, motivo == null ? null : recortar(motivo, 500), salida, duracionMs,
                java.time.Instant.now()));
    }

    /** Cierre de la vuelta: completada, con error o en pausa (espera, aprobación, reintento). */
    private void emitirFin(WorkflowExecution ejecucion) {
        String motivo = WorkflowExecution.ESTADO_ERROR.equals(ejecucion.getStatusExecution())
                ? ejecucion.getErrorMessageExecution() : null;
        emitir(ejecucion, WorkflowEnVivo.FIN, null, null, ejecucion.getStatusExecution(), null, null,
                motivo, null, null);
    }

    private Long duracion(OffsetDateTime inicio, OffsetDateTime fin) {
        return java.time.Duration.between(inicio, fin).toMillis();
    }

    // ============================ Persistencia del rastro ============================

    /**
     * Guarda el paso y deja la ejecución en su nuevo estado, en una transacción
     * aparte de la del nodo: si el nodo falló y revirtió, esto se confirma igual.
     */
    private void guardarPaso(WorkflowExecution ejecucion, Workflow workflow, WorkflowContext contexto,
                             WorkflowNodeDef nodo, WorkflowResult resultado, int secuencia,
                             OffsetDateTime inicio, OffsetDateTime fin,
                             Map<String, Object> antes, String siguiente) {
        transaccion.executeWithoutResult(status -> {
            WorkflowNodeRun run = new WorkflowNodeRun();
            run.setExecutionExecution(ejecucion.getIdWorkflowExecution());
            run.setNodeIdNodeRun(nodo.id());
            run.setNodeTypeNodeRun(nodo.type());
            run.setSequenceNodeRun(secuencia);
            // Solo valen EJECUTANDO, COMPLETADO, ERROR y OMITIDO: es un CHECK en la tabla.
            run.setStatusNodeRun(resultado.esFallo()
                    ? WorkflowNodeRun.ESTADO_ERROR : WorkflowNodeRun.ESTADO_COMPLETADO);
            if (resultado.getMotivo() != null) {
                run.setErrorMessageNodeRun(recortar(resultado.getMotivo(), 1000));
            }
            run.setInputNodeRun(json(entrada(nodo, resultado)));
            run.setOutputNodeRun(json(salida(antes, contexto.variables())));
            run.setStartedAtNodeRun(inicio);
            run.setFinishedAtNodeRun(fin);
            // NOT NULL aunque tenga DEFAULT: Hibernate inserta el null explícito.
            run.setCreatedAtNodeRun(OffsetDateTime.now());
            nodeRunRepository.save(run);

            guardarVariables(ejecucion, contexto);
            if (resultado.esFallo()) {
                finalizarError(ejecucion, nodo.id(), resultado.getMotivo());
                revisarFallosSeguidos(workflow);
            } else if (!resultado.continua()) {
                if (WorkflowExecution.ESTADO_COMPLETADO.equals(resultado.getEstadoFinal())) {
                    finalizar(ejecucion, workflow, resultado.getMotivo());
                } else {
                    // Espera o aprobación: se queda en el nodo de pausa, y al volver
                    // seguirá por el puerto que le asignen.
                    ejecucion.setStatusExecution(resultado.getEstadoFinal());
                    ejecucion.setCurrentNodeExecution(nodo.id());
                    ejecucion.setResultExecution(json(Map.of("motivo", String.valueOf(resultado.getMotivo()))));
                    executionRepository.save(ejecucion);
                    log.info("Ejecución {} en {} tras el nodo '{}': {}", ejecucion.getIdWorkflowExecution(),
                            resultado.getEstadoFinal(), nodo.id(), resultado.getMotivo());
                }
            } else {
                // Se apunta el siguiente nodo antes de ejecutarlo: si el servidor se
                // cae entre medias, la ejecución se retoma ahí y no repite este.
                ejecucion.setCurrentNodeExecution(siguiente);
                executionRepository.save(ejecucion);
            }
        });
    }

    private Map<String, Object> entrada(WorkflowNodeDef nodo, WorkflowResult resultado) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nodo.name());
        m.put("config", nodo.config() == null ? Map.of() : nodo.config());
        if (resultado.getPuerto() != null) {
            m.put("puerto", resultado.getPuerto());
        }
        return m;
    }

    /** Las variables que el nodo creó o cambió: es lo que "produjo" el nodo. */
    private Map<String, Object> salida(Map<String, Object> antes, Map<String, Object> despues) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : despues.entrySet()) {
            Object previo = antes.get(e.getKey());
            if (Objects.equals(previo, e.getValue())) {
                continue;
            }
            // Dentro de un mapa (nodos.*, ejecucion.*) solo lo que cambió: si no,
            // cada paso repetía en su salida los resultados de todos los anteriores.
            if (previo instanceof Map<?, ?> mapaAntes && e.getValue() instanceof Map<?, ?> mapaDespues) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sub = salida((Map<String, Object>) mapaAntes, (Map<String, Object>) mapaDespues);
                if (!sub.isEmpty()) {
                    cambios.put(e.getKey(), sub);
                }
            } else {
                cambios.put(e.getKey(), e.getValue());
            }
        }
        return cambios;
    }

    private int siguienteSecuencia(WorkflowExecution ejecucion, WorkflowContext contexto) {
        // Una ejecución reanudada continúa la numeración; si no, el recorrido del
        // historial mezclaría los pasos de antes y después de la pausa.
        if (contexto.getPasos() == 1) {
            long previos = nodeRunRepository.countByExecutionExecution(ejecucion.getIdWorkflowExecution());
            contexto.setDesplazamientoSecuencia((int) previos);
        }
        return contexto.getDesplazamientoSecuencia() + contexto.getPasos();
    }

    /** Cierra con error en su propia transacción (para fallos fuera de un nodo). */
    private void cerrarConError(WorkflowExecution ejecucion, Workflow workflow, String nodoId, String motivo) {
        transaccion.executeWithoutResult(s -> {
            finalizarError(ejecucion, nodoId, motivo);
            revisarFallosSeguidos(workflow);
        });
    }

    private void finalizar(WorkflowExecution ejecucion, Workflow workflow, String motivo) {
        ejecucion.setStatusExecution(WorkflowExecution.ESTADO_COMPLETADO);
        ejecucion.setFinishedAtExecution(OffsetDateTime.now());
        ejecucion.setCurrentNodeExecution(null);
        ejecucion.setErrorNodeExecution(null);
        if (motivo != null) {
            ejecucion.setResultExecution(json(Map.of("motivo", motivo)));
        }
        executionRepository.save(ejecucion);

        // El contador es una copia desnormalizada para el listado. Cuenta el final
        // bueno, no un nodo intermedio. Se relee para no pisar otra ejecución
        // del mismo workflow que haya terminado a la vez.
        workflowRepository.findById(workflow.getIdWorkflow()).ifPresent(w -> {
            w.setRunCountWorkflow((w.getRunCountWorkflow() == null ? 0 : w.getRunCountWorkflow()) + 1);
            w.setLastRunWorkflow(OffsetDateTime.now());
            workflowRepository.save(w);
        });
        log.info("Ejecución {} completada en el workflow '{}'", ejecucion.getIdWorkflowExecution(),
                workflow.getNameWorkflow());
    }

    private void finalizarError(WorkflowExecution ejecucion, String nodoId, String motivo) {
        ejecucion.setStatusExecution(WorkflowExecution.ESTADO_ERROR);
        ejecucion.setFinishedAtExecution(OffsetDateTime.now());
        ejecucion.setErrorNodeExecution(nodoId);
        ejecucion.setErrorMessageExecution(recortar(motivo, 1000));
        executionRepository.save(ejecucion);
        log.error("Ejecución {} falló en el nodo '{}': {}", ejecucion.getIdWorkflowExecution(), nodoId, motivo);
    }

    /**
     * Si las últimas ejecuciones del workflow fallaron todas, el workflow pasa a
     * ERROR y deja de dispararse. Un workflow roto que se dispara con cada ticket
     * llena el historial de errores sin hacer nada útil; así lo ve el super
     * administrador en la lista, y al publicar una versión nueva vuelve a ACTIVO.
     */
    private void revisarFallosSeguidos(Workflow workflow) {
        List<WorkflowExecution> ultimas = executionRepository
                .findTop5ByWorkflowWorkflowOrderByCreatedAtExecutionDesc(workflow.getIdWorkflow());
        if (ultimas.size() < FALLOS_SEGUIDOS_PARA_ERROR) {
            return;
        }
        boolean todasFallaron = ultimas.stream()
                .allMatch(e -> WorkflowExecution.ESTADO_ERROR.equals(e.getStatusExecution()));
        if (todasFallaron) {
            marcarWorkflowEnError(workflow, FALLOS_SEGUIDOS_PARA_ERROR + " ejecuciones seguidas terminaron en error.");
        }
    }

    private void marcarWorkflowEnError(Workflow workflow, String motivo) {
        workflowRepository.findById(workflow.getIdWorkflow()).ifPresent(w -> {
            if (!Workflow.ESTADO_ACTIVO.equals(w.getStatusWorkflow())) {
                return;
            }
            w.setStatusWorkflow(Workflow.ESTADO_ERROR);
            w.setUpdatedAtWorkflow(OffsetDateTime.now());
            workflowRepository.save(w);
            log.error("El workflow '{}' pasa a ERROR y deja de dispararse: {}", w.getNameWorkflow(), motivo);
            try {
                systemAuditService.record("WORKFLOW_ERROR", "Workflow", w.getIdWorkflow(), "sistema",
                        recortar(motivo, 500));
            } catch (RuntimeException e) {
                log.warn("No se pudo auditar el paso a ERROR del workflow {}: {}", w.getIdWorkflow(), e.getMessage());
            }
        });
    }

    /** Motivo por el que el autor ya no puede actuar, o null si puede. */
    private String autorNoValido(Workflow workflow) {
        if (!actor.ambitoValido(workflow.getAuthorityScope())) {
            return "El workflow tiene un ámbito de autoridad desconocido.";
        }
        User autor = workflow.getCreatedByWorkflow() == null
                ? null : userRepository.findById(workflow.getCreatedByWorkflow()).orElse(null);
        if (autor == null || Boolean.TRUE.equals(autor.getDeletedUser())
                || !"ACTIVE".equalsIgnoreCase(autor.getStatusUser())) {
            return "El autor del workflow ya no está activo, así que el workflow no puede actuar en su nombre. "
                    + "Otro super administrador debe revisarlo y publicarlo de nuevo.";
        }
        return null;
    }

    // ============================ Utilidades ============================

    private Map<String, Object> leerVariables(WorkflowExecution ejecucion) {
        if (ejecucion.getVariablesExecution() == null || ejecucion.getVariablesExecution().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(ejecucion.getVariablesExecution(),
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            log.warn("No se pudieron leer las variables de la ejecución {}: {}. Se empieza vacío.",
                    ejecucion.getIdWorkflowExecution(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Guarda las variables en la ejecución. Antes no se llamaba nunca, así que una
     * ejecución que volvía de una espera perdía todo lo que habían dejado los nodos
     * anteriores (por ejemplo, la clasificación de NEXA).
     */
    private void guardarVariables(WorkflowExecution ejecucion, WorkflowContext contexto) {
        Map<String, Object> vars = contexto.variables();
        // Ni el ámbito ni la copia del payload hace falta guardarlos: se vuelven a
        // poner al cargar, y el payload ya está en su propia columna.
        vars.remove("authorityScope");
        vars.remove("triggerPayload");
        ejecucion.setVariablesExecution(json(vars));
    }

    private Map<String, Object> copia(Map<String, Object> original) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(original),
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return new LinkedHashMap<>(original);
        }
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String recortar(String texto, int max) {
        if (texto == null || texto.length() <= max) {
            return texto;
        }
        return texto.substring(0, max);
    }

    /**
     * Motivo legible de una excepción inesperada. Los errores de base de datos no
     * se copian tal cual: llevan SQL, nombres de tabla y valores. El detalle
     * completo queda en el log de la aplicación.
     */
    private String describir(Throwable e) {
        if (e instanceof DataAccessException) {
            return "Error de base de datos al ejecutar el nodo (detalle en el log del servidor).";
        }
        if (e instanceof AccessDeniedException) {
            return "Permiso denegado: el autor del workflow no puede realizar esta acción.";
        }
        String msg = e.getMessage();
        String tipo = e.getClass().getSimpleName();
        return recortar(msg == null || msg.isBlank() ? tipo : tipo + ": " + msg, 1000);
    }
}
