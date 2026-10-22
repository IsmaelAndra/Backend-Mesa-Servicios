package com.mesaayuda.workflow;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.config.WorkflowProperties;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowNodeRunRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El motor recorriendo el grafo.
 *
 * <p>Se prueba con handlers de mentira porque lo que interesa aquí no es lo que
 * hace cada acción, sino cómo se camina el grafo: qué rama se toma, dónde se para
 * una ejecución y qué pasa cuando un nodo falla. Eso es lo que no se ve cuando
 * todo va bien, que es exactamente cuando hay que probarlo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowEngineRecorridoTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowVersionRepository versionRepository;
    @Mock private WorkflowExecutionRepository executionRepository;
    @Mock private WorkflowNodeRunRepository nodeRunRepository;
    @Mock private WorkflowActor actor;

    @InjectMocks private WorkflowEngine engine;

    private final ObjectMapper mapper = new ObjectMapper();
    private Workflow workflow;
    private WorkflowVersion version;

    @BeforeEach
    void setUp() throws Exception {
        WorkflowProperties props = new WorkflowProperties();
        props.setMaxPasos(20);
        props.setMaxProfundidad(3);
        engine = motor(registro(props), props);

        workflow = new Workflow();
        workflow.setIdWorkflow(id(10));
        workflow.setNameWorkflow("Prueba");
        workflow.setStatusWorkflow(Workflow.ESTADO_ACTIVO);
        workflow.setAuthorityScope("ADMIN");
        workflow.setCreatedByWorkflow(id(1));

        version = new WorkflowVersion();
        version.setIdWorkflowVersion(id(100));
        version.setWorkflowWorkflow(id(10));
        version.setVersionWorkflow(1);

        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(workflow));
        when(versionRepository.findById(id(100))).thenReturn(Optional.of(version));
    }

    /** Transacciones de mentira que cuentan cuántas se confirmaron y cuántas se revirtieron. */
    static final class TransaccionesContadas implements org.springframework.transaction.PlatformTransactionManager {
        int confirmadas;
        int revertidas;

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definicion) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {
            if (status.isRollbackOnly()) {
                revertidas++;
            } else {
                confirmadas++;
            }
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {
            revertidas++;
        }
    }

    private TransaccionesContadas transacciones;
    private UserRepository usuarios;
    private final com.mesaayuda.repository.WorkflowWaitRepository esperas =
            mock(com.mesaayuda.repository.WorkflowWaitRepository.class);

    /** Motor con dependencias de prueba; el autor (id 1) está activo salvo que la prueba diga lo contrario. */
    private WorkflowEngine motor(WorkflowNodeRegistry registro, WorkflowProperties props) {
        transacciones = new TransaccionesContadas();
        usuarios = mock(UserRepository.class);
        com.mesaayuda.entity.User autor = new com.mesaayuda.entity.User();
        autor.setIdUser(id(1));
        autor.setStatusUser("ACTIVE");
        autor.setDeletedUser(false);
        when(usuarios.findById(id(1))).thenReturn(Optional.of(autor));
        return new WorkflowEngine(workflowRepository, versionRepository, executionRepository,
                nodeRunRepository, registro, actorQueEjecuta(), props, mapper, transacciones, usuarios,
                mock(WorkflowVariables.class), mock(com.mesaayuda.service.SystemAuditService.class), esperas,
                mock(WorkflowEnVivo.class));
    }

    /** Registro con un handler de prueba por tipo. */
    private WorkflowNodeRegistry registro(WorkflowProperties props) {
        WorkflowNodeType[] tipos = WorkflowNodeType.values();
        java.util.List<WorkflowNodeHandler> handlers = new java.util.ArrayList<>();
        for (WorkflowNodeType t : tipos) {
            handlers.add(new WorkflowNodeHandler() {
                @Override
                public WorkflowNodeType tipo() {
                    return t;
                }

                @Override
                public WorkflowResult ejecutar(WorkflowContext ctx, WorkflowNodeDef nodo) {
                    return comportamiento(t, ctx, nodo);
                }
            });
        }
        return new WorkflowNodeRegistry(handlers);
    }

    /**
     * El actor sin su montura de seguridad, para que la prueba no tenga que montar
     * un contexto de Spring. Lo que se prueba aquí es el recorrido, y la autoridad
     * la verifica {@code WorkflowActorSeguridadTest}.
     */
    private WorkflowActor actorQueEjecuta() {
        return new WorkflowActor(mock(UserRepository.class)) {
            @Override
            public void ejecutarComo(UUID workflowId, String ambito, UUID autorId, Runnable accion) {
                accion.run();
            }
        };
    }

    /**
 * Comportamiento por tipo, que es lo que cada prueba manipula.
 *
 * <p>El IF y el SWITCH sí enrutan de verdad, con el evaluador real. Un doble que
     * devolviera "avanzar" siempre dejaría pasar pruebas que en producción elegirían
     * otra rama, que es justo lo que no se quiere comprobar aquí.
     */
    private WorkflowResult comportamiento(WorkflowNodeType tipo, WorkflowContext ctx, WorkflowNodeDef nodo) {
        if (tipo == WorkflowNodeType.FIN) {
            return WorkflowResult.parar(WorkflowExecution.ESTADO_COMPLETADO, "Fin.");
        }
        if (tipo == WorkflowNodeType.WAIT) {
            return WorkflowResult.parar(WorkflowExecution.ESTADO_ESPERANDO, "Espera.");
        }
        if (tipo == WorkflowNodeType.IF) {
            boolean cumple = new WorkflowConditionEvaluator().evaluar(ctx, nodo);
            ctx.escribir("ultimoSi", cumple);
            return WorkflowResult.avanzarPor(cumple ? "SI" : "NO");
        }
        return WorkflowResult.avanzar();
    }

    private WorkflowExecution ejecucion() {
        WorkflowExecution e = new WorkflowExecution();
        e.setIdWorkflowExecution(id(500));
        e.setWorkflowWorkflow(id(10));
        e.setVersionWorkflow(id(100));
        e.setStatusExecution(WorkflowExecution.ESTADO_PENDIENTE);
        e.setDepthExecution(0);
        e.setAttemptsExecution(0);
        e.setCreatedAtExecution(OffsetDateTime.now());
        return e;
    }

    private void guardarVersion(String json) {
        version.setDefinitionWorkflow(json);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(ejecucion()));
        when(executionRepository.save(any(WorkflowExecution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(nodeRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private WorkflowExecution capturada() {
        ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(executionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // ============================ Recorrido ============================

    @Test
    @DisplayName("un grafo lineal llega a FIN y termina en COMPLETADO")
    void grafoLinealTermina() {
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":100,"positionY":0}],
                 "edges":[{"from":"ini","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_COMPLETADO, salida.getStatusExecution());
        assertEquals(1L, workflow.getRunCountWorkflow(), "el workflow cuenta una ejecución más");
    }

    @Test
    @DisplayName("deja un rastro por nodo recorrido")
    void dejaRastroPorNodo() {
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"aviso","type":"NOTIFY","name":"A","config":{},"positionX":100,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":200,"positionY":0}],
                 "edges":[
                   {"from":"ini","to":"aviso","sourcePort":"NEXT"},
                   {"from":"aviso","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        // Tres nodos, tres pasos guardados. Sin esto, un proceso que se para a
        // medias no deja forma de saber por dónde iba.
        verify(nodeRunRepository, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    @DisplayName("un nodo de espera para la ejecución en ESPERANDO")
    void esperaDejaEnEspera() {
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"espera","type":"WAIT","name":"E","config":{"minutos":60},"positionX":100,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":200,"positionY":0}],
                 "edges":[
                   {"from":"ini","to":"espera","sourcePort":"NEXT"},
                   {"from":"espera","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        assertEquals(WorkflowExecution.ESTADO_ESPERANDO, capturada().getStatusExecution());
        // Y no llegó al final: solo dos pasos, el de la espera y el de inicio.
        verify(nodeRunRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    @DisplayName("si el nodo falla, la ejecución va a ERROR con el motivo")
    void falloDeNodoVaAError() {
        WorkflowProperties props = new WorkflowProperties();
        props.setMaxPasos(20);
        WorkflowNodeRegistry conFallo = new WorkflowNodeRegistry(
                java.util.stream.Stream.of(WorkflowNodeType.values())
                        .map(t -> (WorkflowNodeHandler) new WorkflowNodeHandler() {
                            @Override
                            public WorkflowNodeType tipo() {
                                return t;
                            }

                            @Override
                            public WorkflowResult ejecutar(WorkflowContext ctx, WorkflowNodeDef nodo) {
                                if (t == WorkflowNodeType.NOTIFY) {
                                    throw new IllegalStateException("Graph no responde");
                                }
                                if (t == WorkflowNodeType.FIN) {
                                    return WorkflowResult.parar(WorkflowExecution.ESTADO_COMPLETADO, "Fin.");
                                }
                                return WorkflowResult.avanzar();
                            }
                        }).toList());
        engine = motor(conFallo, props);
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"aviso","type":"NOTIFY","name":"A","config":{},"positionX":100,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":200,"positionY":0}],
                 "edges":[
                   {"from":"ini","to":"aviso","sourcePort":"NEXT"},
                   {"from":"aviso","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ERROR, salida.getStatusExecution());
        assertEquals("aviso", salida.getErrorNodeExecution(), "el motivo dice en qué nodo se paró");
        assertTrue(salida.getErrorMessageExecution().contains("Graph no responde"),
                "el error debe decir qué pasó: " + salida.getErrorMessageExecution());
    }

    @Test
    @DisplayName("un bucle se corta al superar el límite de pasos")
    void bucleSeCorta() throws Exception {
        WorkflowProperties props = new WorkflowProperties();
        props.setMaxPasos(5);
        // Dos nodos que se apuntan mutuamente: el ciclo que la validación debería
        // haber parado. Se comprueba que el motor tampoco se queda colgado.
        WorkflowNodeRegistry todos = new WorkflowNodeRegistry(
                java.util.stream.Stream.of(WorkflowNodeType.values())
                        .map(t -> (WorkflowNodeHandler) new WorkflowNodeHandler() {
                            @Override
                            public WorkflowNodeType tipo() {
                                return t;
                            }

                            @Override
                            public WorkflowResult ejecutar(WorkflowContext ctx, WorkflowNodeDef nodo) {
                                return WorkflowResult.avanzar();
                            }
                        }).toList());
        engine = motor(todos, props);
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"a","type":"INICIO","name":"A","config":{},"positionX":0,"positionY":0},
                  {"id":"b","type":"NOTIFY","name":"B","config":{},"positionX":100,"positionY":0}],
                 "edges":[
                   {"from":"a","to":"b","sourcePort":"NEXT"},
                   {"from":"b","to":"a","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ERROR, salida.getStatusExecution());
        assertTrue(salida.getErrorMessageExecution().contains("bucle"),
                "el motivo debe señalar que hay un bucle: " + salida.getErrorMessageExecution());
    }

    @Test
    @DisplayName("una ejecución ya terminada no se ejecuta otra vez")
    void noRepiteUnaEjecucionTerminada() {
        WorkflowExecution hecha = ejecucion();
        hecha.setStatusExecution(WorkflowExecution.ESTADO_COMPLETADO);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(hecha));

        engine.ejecutar(id(500));

        // Reprocesarla repetiría sus acciones: volvería a notificar, volvería a
        // cambiar el estado. Es el fallo más caro posible.
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("si la versión ya no existe, falla con un motivo claro")
    void versionAusenteFallaClaro() {
        when(versionRepository.findById(id(100))).thenReturn(Optional.empty());
        guardarVersion("{}");

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ERROR, salida.getStatusExecution());
        assertTrue(salida.getErrorMessageExecution().contains("version"),
                salida.getErrorMessageExecution());
    }

    @Test
    @DisplayName("si la definición está corrupta, falla sin tumbar el worker")
    void definicionCorruptaFalla() {
        guardarVersion("esto no es json");
        // Es el caso del "no se pincha el motor": una versión ilegible es un dato
        // malo, no una avería. El worker tiene que seguir con la siguiente.
        engine.ejecutar(id(500));

        assertEquals(WorkflowExecution.ESTADO_ERROR, capturada().getStatusExecution());
    }

    @Test
    @DisplayName("reanuda por el nodo donde se quedó")
    void reanudaDondeSeQuedo() {
        // Continuación de una ejecución que esperaba. El motor no debe volver al
        // inicio: si lo hiciera, repetiría todo lo anterior.
        WorkflowExecution reanudada = ejecucion();
        reanudada.setStatusExecution(WorkflowExecution.ESTADO_ESPERANDO);
        reanudada.setCurrentNodeExecution("espera");
        version.setDefinitionWorkflow("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"espera","type":"WAIT","name":"E","config":{},"positionX":100,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":200,"positionY":0}],
                 "edges":[
                   {"from":"ini","to":"espera","sourcePort":"NEXT"},
                   {"from":"espera","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(reanudada));
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.ejecutar(id(500));

                java.util.List<String> guardados = org.mockito.Mockito.mockingDetails(nodeRunRepository)
                .getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("save"))
                .map(i -> ((com.mesaayuda.entity.WorkflowNodeRun) i.getArgument(0)).getNodeIdNodeRun())
                .toList();
        assertEquals(List.of("espera"), guardados,
                "solo debe ejecutarse el nodo donde se quedó, no el de inicio: "
                        + "repetir los efectos anteriores sería peor que no hacer nada");
    }

    @Test
    @DisplayName("las variables del payload quedan disponibles para los nodos")
    void variablesDelPayloadDisponibles() {
        WorkflowExecution conPayload = ejecucion();
        conPayload.setTriggerPayload("{\"ticketId\":77,\"prioridad\":\"CRITICA\"}");
        conPayload.setVariablesExecution("{\"ticketId\":77}");
        version.setDefinitionWorkflow("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"cond","type":"IF","name":"C",
                   "config":{"campo":"ticketId","operador":">","valor":10},
                   "positionX":100,"positionY":0},
                  {"id":"si","type":"NOTIFY","name":"S","config":{},"positionX":200,"positionY":-50},
                  {"id":"no","type":"FIN","name":"N","config":{},"positionX":200,"positionY":50}],
                 "edges":[
                   {"from":"ini","to":"cond","sourcePort":"NEXT"},
                   {"from":"cond","to":"si","sourcePort":"SI"},
                   {"from":"cond","to":"no","sourcePort":"NO"}],
                 "variables":{}}
                """);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(conPayload));
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.ejecutar(id(500));

        // La condición 77 > 10 es cierta, así que se recorren ini, cond y si. Si
        // hubiera ido por "no", el rastro sería [ini, cond, no] y no esto.
        java.util.List<String> guardados = org.mockito.Mockito.mockingDetails(nodeRunRepository)
                .getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("save"))
                .map(i -> ((com.mesaayuda.entity.WorkflowNodeRun) i.getArgument(0)).getNodeIdNodeRun())
                .toList();
        assertEquals(List.of("ini", "cond", "si"), guardados);
        assertNotNull(capturada());
    }

    // ============================ Fase 2: reanudar, transacciones, variables ============================

    private static final String GRAFO_CON_ESPERA = """
            {"name":"g","nodes":[
              {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
              {"id":"espera","type":"WAIT","name":"E","config":{"minutos":60},"positionX":100,"positionY":0},
              {"id":"aviso","type":"NOTIFY","name":"A","config":{},"positionX":200,"positionY":0},
              {"id":"fin","type":"FIN","name":"F","config":{},"positionX":300,"positionY":0}],
             "edges":[
               {"from":"ini","to":"espera","sourcePort":"NEXT"},
               {"from":"espera","to":"aviso","sourcePort":"NEXT"},
               {"from":"aviso","to":"fin","sourcePort":"NEXT"}],
             "variables":{}}
            """;

    @Test
    @DisplayName("al volver de una espera sigue por el nodo SIGUIENTE, no repite la espera")
    void reanudaTrasLaEspera() {
        guardarVersion(GRAFO_CON_ESPERA);
        WorkflowExecution despertada = ejecucion();
        despertada.setCurrentNodeExecution("espera");
        despertada.setResumePortExecution(WorkflowEdgeDef.PUERTO_UNICO);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(despertada));

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_COMPLETADO, salida.getStatusExecution(),
                "antes volvía a ejecutar el WAIT, que pausaba otra vez y no terminaba nunca");
        ArgumentCaptor<com.mesaayuda.entity.WorkflowNodeRun> pasos =
                ArgumentCaptor.forClass(com.mesaayuda.entity.WorkflowNodeRun.class);
        verify(nodeRunRepository, org.mockito.Mockito.times(2)).save(pasos.capture());
        assertEquals(List.of("aviso", "fin"),
                pasos.getAllValues().stream().map(com.mesaayuda.entity.WorkflowNodeRun::getNodeIdNodeRun).toList());
        assertEquals(null, salida.getResumePortExecution(), "el puerto de reanudación se consume");
    }

    @Test
    @DisplayName("un rechazo sin rama RECHAZADO termina la ejecución, no sigue como si se hubiera aprobado")
    void rechazoSinRamaTermina() {
        guardarVersion(GRAFO_CON_ESPERA);
        WorkflowExecution rechazada = ejecucion();
        rechazada.setCurrentNodeExecution("espera");
        rechazada.setResumePortExecution(WorkflowExecution.PUERTO_RECHAZADO);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(rechazada));

        engine.ejecutar(id(500));

        assertEquals(WorkflowExecution.ESTADO_COMPLETADO, capturada().getStatusExecution());
        verify(nodeRunRepository, never()).save(any());
    }

    @Test
    @DisplayName("al pausar se guardan las variables que dejaron los nodos anteriores")
    void guardaVariablesAlPausar() {
        WorkflowProperties props = new WorkflowProperties();
        props.setMaxPasos(20);
        engine = motor(registroCon(WorkflowNodeType.NOTIFY, (ctx, nodo) -> {
            ctx.escribir("nodos.mapi1.prioridad", "CRITICA");
            return WorkflowResult.avanzar();
        }), props);
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"mapi1","type":"NOTIFY","name":"N","config":{},"positionX":100,"positionY":0},
                  {"id":"espera","type":"WAIT","name":"E","config":{"minutos":60},"positionX":200,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":300,"positionY":0}],
                 "edges":[
                   {"from":"ini","to":"mapi1","sourcePort":"NEXT"},
                   {"from":"mapi1","to":"espera","sourcePort":"NEXT"},
                   {"from":"espera","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ESPERANDO, salida.getStatusExecution());
        assertEquals("espera", salida.getCurrentNodeExecution());
        assertTrue(salida.getVariablesExecution().contains("CRITICA"),
                "sin esto, la clasificación se perdía al volver de la espera: " + salida.getVariablesExecution());
    }

    @Test
    @DisplayName("un nodo que falla revierte su transacción y el error queda registrado aparte")
    void falloRevierteSoloSuNodo() {
        WorkflowProperties props = new WorkflowProperties();
        props.setMaxPasos(20);
        engine = motor(registroCon(WorkflowNodeType.NOTIFY,
                (ctx, nodo) -> WorkflowResult.fallar("No hay destinatario")), props);
        guardarVersion("""
                {"name":"g","nodes":[
                  {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
                  {"id":"aviso","type":"NOTIFY","name":"A","config":{},"positionX":100,"positionY":0},
                  {"id":"fin","type":"FIN","name":"F","config":{},"positionX":200,"positionY":0}],
                 "edges":[
                   {"from":"ini","to":"aviso","sourcePort":"NEXT"},
                   {"from":"aviso","to":"fin","sourcePort":"NEXT"}],
                 "variables":{}}
                """);

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ERROR, salida.getStatusExecution());
        assertEquals("No hay destinatario", salida.getErrorMessageExecution());
        assertEquals(1, transacciones.revertidas, "solo la transacción del nodo que falló se revierte");
        assertTrue(transacciones.confirmadas >= 3, "carga, nodo de inicio y rastro se confirman");
    }

    @Test
    @DisplayName("si el autor ya no está activo, el workflow no actúa en su nombre")
    void autorInactivoNoEjecuta() {
        guardarVersion(GRAFO_CON_ESPERA);
        com.mesaayuda.entity.User baja = new com.mesaayuda.entity.User();
        baja.setIdUser(id(1));
        baja.setStatusUser("INACTIVE");
        baja.setDeletedUser(false);
        when(usuarios.findById(id(1))).thenReturn(Optional.of(baja));

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ERROR, salida.getStatusExecution());
        assertTrue(salida.getErrorMessageExecution().contains("autor"));
        verify(nodeRunRepository, never()).save(any());
        assertEquals(Workflow.ESTADO_ERROR, workflow.getStatusWorkflow());
    }

    @Test
    @DisplayName("una ejecución cancelada mientras corre se detiene antes del siguiente nodo")
    void canceladaSeDetiene() {
        guardarVersion(GRAFO_CON_ESPERA);
        WorkflowExecution enCurso = ejecucion();
        WorkflowExecution cancelada = ejecucion();
        cancelada.setStatusExecution(WorkflowExecution.ESTADO_CANCELADO);
        // La primera lectura es la carga; la segunda, la comprobación del bucle.
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(enCurso), Optional.of(cancelada));

        engine.ejecutar(id(500));

        verify(nodeRunRepository, never()).save(any());
    }

    // ============================ Reintentos de fallos pasajeros ============================

    private static final String GRAFO_IA = """
            {"name":"g","nodes":[
              {"id":"ini","type":"INICIO","name":"I","config":{},"positionX":0,"positionY":0},
              {"id":"mapi","type":"MAPI","name":"N","config":{},"positionX":100,"positionY":0},
              {"id":"fin","type":"FIN","name":"F","config":{},"positionX":200,"positionY":0}],
             "edges":[
               {"from":"ini","to":"mapi","sourcePort":"NEXT"},
               {"from":"mapi","to":"fin","sourcePort":"NEXT"}],
             "variables":{}}
            """;

    @Test
    @DisplayName("un fallo pasajero deja la ejecución esperando un reintento del mismo nodo")
    void falloPasajeroSeReintenta() {
        WorkflowProperties props = new WorkflowProperties();
        engine = motor(registroCon(WorkflowNodeType.MAPI,
                (ctx, nodo) -> WorkflowResult.fallarReintentable("IA saturada")), props);
        guardarVersion(GRAFO_IA);

        engine.ejecutar(id(500));

        WorkflowExecution salida = capturada();
        assertEquals(WorkflowExecution.ESTADO_ESPERANDO, salida.getStatusExecution(),
                "un pico de demanda de la IA no debe dar la ejecución por perdida");
        assertEquals("mapi", salida.getCurrentNodeExecution());
        ArgumentCaptor<com.mesaayuda.entity.WorkflowWait> espera =
                ArgumentCaptor.forClass(com.mesaayuda.entity.WorkflowWait.class);
        verify(esperas).save(espera.capture());
        assertEquals(com.mesaayuda.entity.WorkflowWait.MOTIVO_REINTENTO, espera.getValue().getCommentWait());
        assertTrue(salida.getVariablesExecution().contains("\"reintentos\""));
    }

    @Test
    @DisplayName("al volver de un reintento se ejecuta otra vez el mismo nodo")
    void reintentoVuelveAEjecutarElNodo() {
        guardarVersion(GRAFO_IA);
        WorkflowExecution despertada = ejecucion();
        despertada.setCurrentNodeExecution("mapi");
        despertada.setResumePortExecution(WorkflowExecution.PUERTO_REINTENTO);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(despertada));

        engine.ejecutar(id(500));

        assertEquals(WorkflowExecution.ESTADO_COMPLETADO, capturada().getStatusExecution());
        ArgumentCaptor<com.mesaayuda.entity.WorkflowNodeRun> pasos =
                ArgumentCaptor.forClass(com.mesaayuda.entity.WorkflowNodeRun.class);
        verify(nodeRunRepository, org.mockito.Mockito.times(2)).save(pasos.capture());
        assertEquals(List.of("mapi", "fin"),
                pasos.getAllValues().stream().map(com.mesaayuda.entity.WorkflowNodeRun::getNodeIdNodeRun).toList());
    }

    @Test
    @DisplayName("agotados los reintentos, el fallo pasajero termina en ERROR")
    void reintentosAgotadosVanAError() {
        WorkflowProperties props = new WorkflowProperties();
        props.setMaxReintentosNodo(3);
        engine = motor(registroCon(WorkflowNodeType.MAPI,
                (ctx, nodo) -> WorkflowResult.fallarReintentable("IA saturada")), props);
        guardarVersion(GRAFO_IA);
        WorkflowExecution tercera = ejecucion();
        tercera.setCurrentNodeExecution("mapi");
        tercera.setResumePortExecution(WorkflowExecution.PUERTO_REINTENTO);
        tercera.setVariablesExecution("{\"reintentos\":{\"mapi\":3}}");
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(tercera));

        engine.ejecutar(id(500));

        assertEquals(WorkflowExecution.ESTADO_ERROR, capturada().getStatusExecution());
        verify(esperas, never()).save(any());
    }

    /** Registro donde un tipo concreto tiene un comportamiento propio y el resto avanza. */
    private WorkflowNodeRegistry registroCon(WorkflowNodeType especial,
            java.util.function.BiFunction<WorkflowContext, WorkflowNodeDef, WorkflowResult> accion) {
        return new WorkflowNodeRegistry(java.util.stream.Stream.of(WorkflowNodeType.values())
                .map(t -> (WorkflowNodeHandler) new WorkflowNodeHandler() {
                    @Override
                    public WorkflowNodeType tipo() {
                        return t;
                    }

                    @Override
                    public WorkflowResult ejecutar(WorkflowContext ctx, WorkflowNodeDef nodo) {
                        if (t == especial) {
                            return accion.apply(ctx, nodo);
                        }
                        return comportamiento(t, ctx, nodo);
                    }
                }).toList());
    }
}