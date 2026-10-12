package com.mesaayuda.workflow;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.config.WorkflowProperties;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.repository.WorkflowEventDedupRepository;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Encolar ejecuciones y no duplicarlas.
 *
 * <p>La deduplicación es lo que evita que un ticket que pasa por cinco estados
 * dispare cinco veces el mismo proceso y mande cinco correos. Y los cupos existen
 * para que un workflow mal configurado no se lleve el motor entero y deje a los
 * demás sin ejecutar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowTriggerServiceTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowVersionRepository versionRepository;
    @Mock private WorkflowExecutionRepository executionRepository;
    @Mock private WorkflowEventDedupRepository dedupRepository;

    private WorkflowTriggerService service;
    private WorkflowProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WorkflowProperties();
        service = new WorkflowTriggerService(workflowRepository, versionRepository,
                executionRepository, dedupRepository, properties, new ObjectMapper());
    }

    private Workflow activo(UUID id) {
        Workflow w = new Workflow();
        w.setIdWorkflow(id);
        w.setNameWorkflow("Proceso " + id);
        w.setStatusWorkflow(Workflow.ESTADO_ACTIVO);
        w.setTriggerType("TICKET_CREATED");
        w.setAuthorityScope("SUPER_ADMIN");
        w.setCreatedByWorkflow(id(1));
        return w;
    }

    private WorkflowVersion vigente() {
        WorkflowVersion v = new WorkflowVersion();
        v.setIdWorkflowVersion(id(100));
        v.setWorkflowWorkflow(id(10));
        v.setVersionWorkflow(2);
        v.setIsActiveVersion(true);
        return v;
    }

    private void listoParaEncolar(Workflow w) {
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of(w));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(w.getIdWorkflow()))
                .thenReturn(Optional.of(vigente()));
        when(executionRepository.save(any(WorkflowExecution.class))).thenAnswer(inv -> {
            WorkflowExecution e = inv.getArgument(0);
            e.setIdWorkflowExecution(id(500));
            return e;
        });
        when(dedupRepository.reservarEvento(any(), anyString(), any())).thenReturn(1);
        when(executionRepository.countByStatusExecutionAndWorkflowWorkflow(anyString(), any()))
                .thenReturn(0L);
        when(executionRepository.consultarHistorial(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    // ============================ Encolar ============================

    @Test
    @DisplayName("un evento encola una ejecución por workflow activo")
    void encolaPorWorkflow() {
        Workflow w = activo(id(10));
        listoParaEncolar(w);

        int creadas = service.disparar("TICKET_CREATED", "77", Map.of("ticketId", 77));

        assertEquals(1, creadas);
        verify(executionRepository).save(any(WorkflowExecution.class));
    }

    @Test
    @DisplayName("la ejecución guarda con la versión vigente en ese momento")
    void fijaLaVersionVigente() {
        Workflow w = activo(id(10));
        listoParaEncolar(w);

        service.disparar("TICKET_CREATED", "77", Map.of());

        ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(executionRepository).save(captor.capture());
        // Si mañana se publica otra versión, esta ejecución sigue con el grafo con
        // el que arrancó, y el historial dice la verdad.
        assertEquals(id(100), captor.getValue().getVersionWorkflow());
        assertEquals(WorkflowExecution.ESTADO_PENDIENTE, captor.getValue().getStatusExecution());
        assertEquals("77", captor.getValue().getCorrelationKey());
        assertEquals(0, captor.getValue().getDepthExecution());
    }

    @Test
    @DisplayName("un evento sin workflows activos no encola nada")
    void sinWorkflowsNoEncola() {
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of());

        assertEquals(0, service.disparar("TICKET_CREATED", "77", Map.of()));
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin versión publicada no encola trabajo imposible")
    void sinVersionNoEncola() {
        Workflow w = activo(id(10));
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of(w));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.empty());

        // Un workflow activo sin grafo no se puede ejecutar. Encolarlo llenaría
        // la cola de trabajo que va a fallar seguro.
        assertEquals(0, service.disparar("TICKET_CREATED", "77", Map.of()));
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("un motor apagado no encola nada")
    void motorApagadoNoEncola() {
        properties.setEnabled(false);
        Workflow w = activo(id(10));
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of(w));

        assertEquals(0, service.disparar("TICKET_CREATED", "77", Map.of()));
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("un evento sin clave no se encola")
    void eventoSinClaveNoSeEncola() {
        assertEquals(0, service.disparar(null, "77", Map.of()));
        assertEquals(0, service.disparar("TICKET_CREATED", null, Map.of()));
        assertEquals(0, service.disparar("  ", "77", Map.of()));
    }

    // ============================ Deduplicación ============================

    @Test
    @DisplayName("el mismo evento dos veces solo se encola una")
    void deduplicaElMismoEvento() {
        Workflow w = activo(id(10));
        listoParaEncolar(w);
        // El indice unico de workflow_event_dedup rechaza la segunda vez.
        when(dedupRepository.reservarEvento(any(), anyString(), any())).thenReturn(1, 0);

        assertEquals(1, service.disparar("TICKET_CREATED", "77", Map.of()));
        assertEquals(0, service.disparar("TICKET_CREATED", "77", Map.of()));

        // La segunda ejecución se crea y se borra: la clave la referencia.
        verify(executionRepository).delete(any(WorkflowExecution.class));
    }

    @Test
    @DisplayName("la deduplicación es por workflow, no global")
    void deduplicaPorWorkflow() {
        Workflow a = activo(id(10));
        Workflow b = activo(id(20));
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of(a, b));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.of(vigente()));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(20)))
                .thenReturn(Optional.of(vigente()));
        when(executionRepository.save(any(WorkflowExecution.class))).thenAnswer(inv -> {
            WorkflowExecution e = inv.getArgument(0);
            e.setIdWorkflowExecution(id(500));
            return e;
        });
        when(dedupRepository.reservarEvento(any(), anyString(), any())).thenReturn(1);
        when(executionRepository.countByStatusExecutionAndWorkflowWorkflow(anyString(), any()))
                .thenReturn(0L);
        when(executionRepository.consultarHistorial(any(), any(), any(), any()))
                .thenReturn(List.of());

        // El mismo ticket puede disparar dos procesos distintos, y ambos deben
        // arrancar. Deduplicar globalmente dejaría el segundo sin ejecutar nunca.
        assertEquals(2, service.disparar("TICKET_CREATED", "77", Map.of()));
        verify(dedupRepository, org.mockito.Mockito.times(2)).reservarEvento(any(), anyString(), any());
    }

    @Test
    @DisplayName("la clave que se guarda es la que recibe, sin alterarla")
    void guardaLaClaveTalCual() {
        // El tipo va delante de la clave en WorkflowEventPublisher, que es quien
        // conoce los eventos. El servicio guarda lo que le llega sin tocarlo.
        Workflow w = activo(id(10));
        listoParaEncolar(w);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        service.disparar("TICKET_CREATED", "TICKET_CREATED:77", Map.of());
        verify(dedupRepository).reservarEvento(eq(id(10)), captor.capture(), any());

        assertEquals("TICKET_CREATED:77", captor.getValue());
    }

    // ============================ Cupos ============================

    @Test
    @DisplayName("un workflow con demasiadas ejecuciones a la vez no encola más")
    void respetaSimultaneas() {
        Workflow w = activo(id(10));
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of(w));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.of(vigente()));
        when(executionRepository.countByStatusExecutionAndWorkflowWorkflow(
                eq(WorkflowExecution.ESTADO_EJECUTANDO), eq(id(10))))
                .thenReturn((long) properties.getMaxEjecucionesSimultaneas());

        // Sin este techo, un disparador masivo se comería el pool del motor y los
        // demás procesos no se ejecutarían hasta que terminara.
        assertEquals(0, service.disparar("TICKET_CREATED", "77", Map.of()));
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("un workflow que supera su cuota por hora deja de encolar")
    void respetaCuotaHoraria() {
        Workflow w = activo(id(10));
        when(workflowRepository.findActivosPorTrigger(anyString())).thenReturn(List.of(w));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.of(vigente()));
        when(executionRepository.countByStatusExecutionAndWorkflowWorkflow(anyString(), any()))
                .thenReturn(0L);
        // Cuenta todas las creadas en la última hora, no solo las pendientes: el
        // worker las toma en un segundo y antes el cupo no llegaba a llenarse.
        when(executionRepository.countByWorkflowWorkflowAndCreatedAtExecutionAfter(any(), any()))
                .thenReturn((long) properties.getMaxEjecucionesPorHora());

        assertEquals(0, service.disparar("TICKET_CREATED", "77", Map.of()));
    }

    @Test
    @DisplayName("un workflow con hueco entra con normalidad")
    void conHuecoEncola() {
        listoParaEncolar(activo(id(10)));

        assertEquals(1, service.disparar("TICKET_CREATED", "77", Map.of()));
    }

    // ============================ Payload ============================

    @Test
    @DisplayName("el payload se guarda como JSON legible")
    void guardaElPayload() throws Exception {
        Workflow w = activo(id(10));
        listoParaEncolar(w);

        service.disparar("TICKET_CREATED", "77", Map.of("ticketId", 77, "prioridad", "CRITICA"));

        ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(executionRepository).save(captor.capture());
        String payload = captor.getValue().getTriggerPayload();

        assertNotNull(payload);
        assertTrue(payload.contains("CRITICA"), payload);
        assertFalse(payload.isBlank());
    }

    @Test
    @DisplayName("un payload nulo se guarda como objeto vacío")
    void payloadNuloEsObjetoVacio() {
        Workflow w = activo(id(10));
        listoParaEncolar(w);

        service.disparar("TICKET_CREATED", "77", null);

        ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(executionRepository).save(captor.capture());
        // El motor siempre va a leer este campo. Dejarlo nulo obligaría a
        // comprobarlo en cada lectura sin ganar nada.
        assertEquals("{}", captor.getValue().getTriggerPayload());
    }

    @Test
    @DisplayName("purgar la deduplicación llama al repositorio con un límite antiguo")
    void purgaDeduplicacion() {
        when(dedupRepository.borrarAntiguos(any())).thenReturn(3);

        assertEquals(3, service.limpiarDeduplicacion());
    }

    // ============================ Fase 2: cascadas ============================

    private WorkflowEvento evento(WorkflowOrigen.Datos origen) {
        return new WorkflowEvento("TICKET_CREATED", "TICKET_CREATED:77:abc", Map.of("ticketId", 77L),
                "TICKET", id(77), origen);
    }

    @Test
    @DisplayName("un evento provocado por un workflow hereda su profundidad + 1 y su origen")
    void heredaProfundidadYOrigen() {
        listoParaEncolar(activo(id(10)));

        assertEquals(1, service.disparar(evento(new WorkflowOrigen.Datos(id(900), id(99), 1))));

        ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
        verify(executionRepository).save(captor.capture());
        // Antes todas empezaban en 0 y el tope de profundidad no cortaba nunca.
        assertEquals(2, captor.getValue().getDepthExecution());
        assertEquals(id(900), captor.getValue().getOriginExecution());
        assertEquals("TICKET", captor.getValue().getEntityTypeExecution());
        assertEquals(id(77), captor.getValue().getEntityIdExecution());
    }

    @Test
    @DisplayName("una cascada que supera la profundidad máxima no encola nada")
    void cascadaProfundaSeCorta() {
        listoParaEncolar(activo(id(10)));
        int max = properties.getMaxProfundidad();

        assertEquals(0, service.disparar(evento(new WorkflowOrigen.Datos(id(900), id(99), max))));
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("las acciones de un workflow no lo vuelven a disparar a él mismo")
    void noSeDisparaASiMismo() {
        listoParaEncolar(activo(id(10)));

        // "Cuando se actualice el ticket, cambia su prioridad": sin esto, un bucle inmediato.
        assertEquals(0, service.disparar(evento(new WorkflowOrigen.Datos(id(900), id(10), 0))));
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("la ejecución manual registra a la persona que la lanzó y la entidad elegida")
    void manualRegistraIniciador() {
        Workflow w = activo(id(10));
        listoParaEncolar(w);

        WorkflowExecution e = service.ejecutarManualmente(w, Map.of("ticketId", id(55)), id(42));

        assertEquals(id(42), e.getStartedByExecution(), "antes quedaba el autor del workflow, no quien la lanzó");
        assertEquals("MANUAL", e.getTriggerType());
        assertEquals("TICKET", e.getEntityTypeExecution());
        assertEquals(id(55), e.getEntityIdExecution());
    }
}