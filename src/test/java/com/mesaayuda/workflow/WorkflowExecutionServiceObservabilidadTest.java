package com.mesaayuda.workflow;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowNodeRun;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowNodeRunRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowWaitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Observabilidad del motor: historial, traza y cancelación.
 *
 * <p>Aquí se responde a "¿qué pasó con este proceso?". Sin esto, un workflow que se
 * para es un misterio: no hay forma de saber en qué nodo se quedó ni qué había
 * hecho antes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowExecutionServiceObservabilidadTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowExecutionRepository executionRepository;
    @Mock private WorkflowNodeRunRepository nodeRunRepository;
    @Mock private WorkflowWaitRepository waitRepository;

    private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowExecutionService(workflowRepository, executionRepository,
                nodeRunRepository, waitRepository,
                org.mockito.Mockito.mock(com.mesaayuda.repository.WorkflowEventDedupRepository.class),
                org.mockito.Mockito.mock(com.mesaayuda.repository.WorkflowVersionRepository.class),
                org.mockito.Mockito.mock(com.mesaayuda.repository.UserRepository.class),
                org.mockito.Mockito.mock(com.mesaayuda.repository.TicketRepository.class));
    }

    private WorkflowExecution ejecucion(String estado) {
        WorkflowExecution e = new WorkflowExecution();
        e.setIdWorkflowExecution(id(500));
        e.setWorkflowWorkflow(id(10));
        e.setVersionWorkflow(id(100));
        e.setStatusExecution(estado);
        e.setCorrelationKey("TICKET_CREATED:77");
        e.setDepthExecution(0);
        e.setAttemptsExecution(1);
        return e;
    }

    // ============================ Traza ============================

    @Test
    @DisplayName("los pasos se devuelven en el orden en que se recorrieron")
    void pasosEnOrden() {
        WorkflowNodeRun a = paso(1, "ini");
        WorkflowNodeRun b = paso(2, "crit");
        WorkflowNodeRun c = paso(3, "fin");
        when(nodeRunRepository.findByExecutionExecutionOrderBySequenceNodeRunAsc(id(500)))
                .thenReturn(List.of(a, b, c));

        List<WorkflowNodeRun> pasos = service.pasos(id(500));

        // El orden importa: es lo que permite reconstruir qué pasó antes de qué.
        assertEquals(List.of("ini", "crit", "fin"),
                pasos.stream().map(WorkflowNodeRun::getNodeIdNodeRun).toList());
    }

    private WorkflowNodeRun paso(int secuencia, String nodo) {
        WorkflowNodeRun r = new WorkflowNodeRun();
        r.setExecutionExecution(id(500));
        r.setNodeIdNodeRun(nodo);
        r.setSequenceNodeRun(secuencia);
        r.setStatusNodeRun(WorkflowNodeRun.ESTADO_COMPLETADO);
        return r;
    }

    // ============================ Cancelacion ============================

    @Test
    @DisplayName("cancelar marca CANCELADO y deja fecha de fin")
    void cancelarMarcaCancelado() {
        WorkflowExecution e = ejecucion(WorkflowExecution.ESTADO_EJECUTANDO);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(e));
        when(waitRepository.findByExecutionExecutionOrderByCreatedAtWaitAsc(id(500))).thenReturn(List.of());
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelar(id(500));

        assertEquals(WorkflowExecution.ESTADO_CANCELADO, e.getStatusExecution());
        assertTrue(e.getFinishedAtExecution() != null, "debe quedar cuándo terminó");
    }

    @Test
    @DisplayName("cancelar cierra las esperas pendientes")
    void cancelarCierraEsperas() {
        // Si la espera se dejara pendiente, el worker volvería a poner la ejecución
        // en cola y una cancelada reaparecería sola más tarde.
        WorkflowExecution e = ejecucion(WorkflowExecution.ESTADO_ESPERANDO);
        when(executionRepository.findById(id(500))).thenReturn(Optional.of(e));
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowWait pendiente = new WorkflowWait();
        pendiente.setExecutionExecution(id(500));
        pendiente.setNodeIdWait("espera");
        pendiente.setStatusWait(WorkflowWait.ESTADO_PENDIENTE);
        WorkflowWait yaResuelta = new WorkflowWait();
        yaResuelta.setStatusWait(WorkflowWait.ESTADO_APROBADA);
        when(waitRepository.findByExecutionExecutionOrderByCreatedAtWaitAsc(id(500)))
                .thenReturn(List.of(pendiente, yaResuelta));

        service.cancelar(id(500));

        assertEquals(WorkflowWait.ESTADO_RECHAZADA, pendiente.getStatusWait());
        // La que ya estaba resuelta no se toca: su decisión ya ocurrió.
        assertEquals(WorkflowWait.ESTADO_APROBADA, yaResuelta.getStatusWait());
        verify(waitRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    @DisplayName("una ejecucion ya terminada no se puede cancelar")
    void noSeCancelaLoTerminado() {
        // Reabrir una ejecución completada para marcarla cancelada falsearía el
        // historial, que es justo lo que este historial pretende evitar.
        for (String estado : List.of(WorkflowExecution.ESTADO_COMPLETADO,
                WorkflowExecution.ESTADO_ERROR, WorkflowExecution.ESTADO_CANCELADO)) {
            WorkflowExecution e = ejecucion(estado);
            when(executionRepository.findById(id(500))).thenReturn(Optional.of(e));

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> service.cancelar(id(500)));
            assertTrue(ex.getMessage().contains(estado),
                    "el mensaje debe decir en qué estado está: " + ex.getMessage());
        }
        verify(executionRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelar una ejecucion que no existe da 404")
    void cancelarInexistente() {
        when(executionRepository.findById(id(999))).thenReturn(Optional.empty());

        assertThrows(com.mesaayuda.exception.ResourceNotFoundException.class,
                () -> service.cancelar(id(999)));
    }

    // ============================ Historial ============================

    @Test
    @DisplayName("sin filtro de estado se consulta por workflow")
    void historialSinFiltro() {
        var page = new PageImpl<>(List.of(ejecucion(WorkflowExecution.ESTADO_COMPLETADO)));
        when(executionRepository.findByWorkflowWorkflowOrderByCreatedAtExecutionDesc(
                eq(id(10)), any())).thenReturn(page);

        service.consultar(id(10), null, PageRequest.of(0, 25));

        verify(executionRepository).findByWorkflowWorkflowOrderByCreatedAtExecutionDesc(eq(id(10)), any());
    }

    @Test
    @DisplayName("el filtro de estado se normaliza a mayusculas")
    void historialConFiltroNormalizado() {
        var page = new PageImpl<>(List.of(ejecucion(WorkflowExecution.ESTADO_ERROR)));
        when(executionRepository.findByWorkflowWorkflowAndStatusExecutionOrderByCreatedAtExecutionDesc(
                eq(id(10)), eq("ERROR"), any())).thenReturn(page);

        // Sin normalizar, el filtro "error" no encontraría nada: los estados en la
        // base van en mayúsculas y la comparación es exacta.
        service.consultar(id(10), "error", PageRequest.of(0, 25));

        verify(executionRepository).findByWorkflowWorkflowAndStatusExecutionOrderByCreatedAtExecutionDesc(
                eq(id(10)), eq("ERROR"), any());
    }

    @Test
    @DisplayName("un estado en blanco se trata como sin filtro")
    void estadoEnBlancoEsSinFiltro() {
        var page = new PageImpl<>(List.of(ejecucion(WorkflowExecution.ESTADO_PENDIENTE)));
        when(executionRepository.findByWorkflowWorkflowOrderByCreatedAtExecutionDesc(
                eq(id(10)), any())).thenReturn(page);

        service.consultar(id(10), "   ", PageRequest.of(0, 25));

        verify(executionRepository).findByWorkflowWorkflowOrderByCreatedAtExecutionDesc(eq(id(10)), any());
    }

    // ============================ Nombre del workflow ============================

    @Test
    @DisplayName("el nombre del workflow se resuelve para el listado")
    void nombreDelWorkflow() {
        com.mesaayuda.entity.Workflow w = new com.mesaayuda.entity.Workflow();
        w.setIdWorkflow(id(10));
        w.setNameWorkflow("Escalamiento SLA");
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));

        assertEquals("Escalamiento SLA", service.nombreWorkflow(ejecucion(WorkflowExecution.ESTADO_PENDIENTE)));
    }

    @Test
    @DisplayName("un workflow borrado no rompe el historial")
    void workflowBorradoNoRompe() {
        when(workflowRepository.findById(id(10))).thenReturn(Optional.empty());

        // El borrado es lógico, pero si algún día se limpia la tabla, el historial
        // de ejecuciones tiene que seguir siendo legible.
        assertEquals("(workflow borrado)",
                service.nombreWorkflow(ejecucion(WorkflowExecution.ESTADO_PENDIENTE)));
    }

    // ============================ Retencion ============================

    @Test
    @DisplayName("una retencion de cero dias se rechaza")
    void retencionCeroSeRechaza() {
        // Cero significaría borrar el historial entero. Eso casi siempre es un
        // error de configuración, no una intención.
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.purgar(0));
        assertTrue(e.getMessage().contains("al menos un día"), e.getMessage());
        assertThrows(BadRequestException.class, () -> service.purgar(-5));
    }

    @Test
    @DisplayName("purgar borra la traza, las esperas y la deduplicación antes que la ejecucion")
    void purgarBorraHijasPrimero() {
        WorkflowExecution vieja = ejecucion(WorkflowExecution.ESTADO_COMPLETADO);
        when(executionRepository.terminadasAntesDe(any(), any(), any())).thenReturn(List.of(vieja));

        int borradas = service.purgar(90);

        assertEquals(1, borradas);
        // Las hijas primero: las claves foráneas apuntan a la ejecución. Antes solo
        // se borraba la traza y una ejecución con espera hacía fallar la purga.
        verify(nodeRunRepository).deleteByExecutionExecution(id(500));
        verify(waitRepository).borrarDeEjecucion(id(500));
        verify(executionRepository).delete(vieja);
    }

    @Test
    @DisplayName("purgar pide las terminadas ANTES del límite, en error y canceladas incluidas")
    void purgarIncluyeErroresYSoloViejas() {
        WorkflowExecution completada = ejecucion(WorkflowExecution.ESTADO_COMPLETADO);
        WorkflowExecution fallida = ejecucion(WorkflowExecution.ESTADO_ERROR);
        when(executionRepository.terminadasAntesDe(any(), any(), any())).thenReturn(List.of(completada, fallida));

        assertEquals(2, service.purgar(90));

        ArgumentCaptor<java.util.Collection<String>> estados = ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<java.time.OffsetDateTime> limite = ArgumentCaptor.forClass(java.time.OffsetDateTime.class);
        verify(executionRepository).terminadasAntesDe(estados.capture(), limite.capture(), any());
        assertTrue(estados.getValue().containsAll(List.of(WorkflowExecution.ESTADO_COMPLETADO,
                WorkflowExecution.ESTADO_ERROR, WorkflowExecution.ESTADO_CANCELADO)));
        // El límite es "hace 90 días": lo de antes se borra. La versión anterior
        // usaba "creadas desde", que borraba justo lo reciente.
        assertTrue(limite.getValue().isBefore(java.time.OffsetDateTime.now().minusDays(89)));
    }

    @Test
    @DisplayName("la duracion es cero si la ejecucion no termino")
    void duracionDeEjecucionIncompleta() {
        WorkflowExecution sinTerminar = ejecucion(WorkflowExecution.ESTADO_EJECUTANDO);
        sinTerminar.setStartedAtExecution(java.time.OffsetDateTime.now());
        sinTerminar.setFinishedAtExecution(null);

        assertEquals(java.time.Duration.ZERO, WorkflowExecutionService.duracion(sinTerminar));
    }
}