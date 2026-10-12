package com.mesaayuda.workflow;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cuándo se publica un evento de workflow.
 *
 * <p>El motivo de que exista el publicador es el momento. Si un disparador
 * arrancara dentro de la transacción que crea el ticket, el worker podría tomar
 * la ejecución antes de que esa transacción confirmara, y el proceso leería un
 * ticket que todavía no existe. Peor: si la transacción se deshacía, el proceso ya
 * habría actuado sobre un ticket que nunca existió.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowEventPublisherTest {

    @Mock private WorkflowTriggerService triggerService;

    private WorkflowEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WorkflowEventPublisher(triggerService);
        limpiarSincronizacion();
    }

    /** ClearSynchronization lanza si no hay ninguna activa, y JUnit no garantiza que la haya. */
    private void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Ticket ticket() {
        Area area = new Area();
        area.setNameArea("TI");
        Category categoria = new Category();
        categoria.setNameCategory("Hardware");
        Priority prioridad = new Priority();
        prioridad.setNamePriority("CRITICA");
        Status estado = new Status();
        estado.setNameStatus("NUEVO");

        User solicitante = new User();
        solicitante.setIdUser(id(9));
        solicitante.setCorporateEmailUser("quien@coacmagisterio.fin.ec");

        Ticket t = new Ticket();
        t.setIdTicket(id(77));
        t.setTitleTicket("Impresora caída");
        t.setDescriptionTicket("No imprime");
        t.setAreaTicket(area);
        t.setCategoryTicket(categoria);
        t.setPriorityTicket(prioridad);
        t.setStatusTicket(estado);
        t.setUserTicket(solicitante);
        return t;
    }

    @Test
    @DisplayName("dentro de una transaccion, el evento espera al commit")
    void esperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.ticketCreado(ticket());

        // Nada todavía: el ticket puede no existir dentro de dos segundos.
        verifyNoInteractions(triggerService);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        limpiarSincronizacion();

        verify(triggerService).disparar(tipo("TICKET_CREATED"));
    }

    @Test
    @DisplayName("sin transaccion, el evento se publica de inmediato")
    void sinTransaccionPublicaYa() {
        // Es el caso de las tareas programadas y del arranque: no hay nada que
        // confirmar, así que esperar sería quedarse parado sin motivo.
        publisher.ticketCreado(ticket());

        verify(triggerService).disparar(tipo("TICKET_CREATED"));
    }

    @Test
    @DisplayName("el ticket viaja con los datos que un workflow puede leer")
    void incluyeLosDatosDelTicket() {
        publisher.ticketCreado(ticket());

        org.mockito.ArgumentCaptor<WorkflowEvento> captor = org.mockito.ArgumentCaptor.forClass(WorkflowEvento.class);
        verify(triggerService).disparar(captor.capture());
        Map<String, Object> datos = captor.getValue().datos();

        assertEquals(id(77), datos.get("ticketId"));
        assertEquals("CRITICA", datos.get("prioridad"));
        assertEquals("Hardware", datos.get("categoria"));
        assertEquals("TI", datos.get("area"));
        assertEquals("NUEVO", datos.get("estado"));
        assertEquals(id(9), datos.get("solicitanteId"));
        assertEquals("Impresora caída", datos.get("titulo"));
    }

    @Test
    @DisplayName("un ticket sin relaciones no revienta el evento")
    void ticketConNulos() {
        // Un workflow disparado por un evento de SLA puede llegar sin área o sin
        // categoría. Publicar el evento con huecos es mejor que no publicarlo.
        Ticket vacio = new Ticket();
        vacio.setIdTicket(id(1));

        publisher.ticketCreado(vacio);

        verify(triggerService).disparar(tipo("TICKET_CREATED"));
    }

    @Test
    @DisplayName("cada tipo de evento usa su propio nombre")
    void nombresDeEvento() {
        publisher.ticketCreado(ticket());
        publisher.ticketCambioEstado(ticket(), "NUEVO");
        publisher.ticketAsignado(ticket());
        publisher.ticketCerrado(ticket());
        publisher.ticketReabierto(ticket());
        publisher.ticketActualizado(ticket());

        verify(triggerService).disparar(tipo("TICKET_CREATED"));
        verify(triggerService).disparar(tipo("TICKET_STATUS_CHANGED"));
        verify(triggerService).disparar(tipo("TICKET_ASSIGNED"));
        verify(triggerService).disparar(tipo("TICKET_CLOSED"));
        verify(triggerService).disparar(tipo("TICKET_REOPENED"));
        verify(triggerService).disparar(tipo("TICKET_UPDATED"));
    }

    @Test
    @DisplayName("el cambio de estado lleva el estado anterior y el nuevo")
    void llevaElEstadoAnterior() {
        publisher.ticketCambioEstado(ticket(), "ASIGNADO");

        org.mockito.ArgumentCaptor<WorkflowEvento> captor = org.mockito.ArgumentCaptor.forClass(WorkflowEvento.class);
        verify(triggerService).disparar(captor.capture());

        assertEquals("ASIGNADO", captor.getValue().datos().get("estadoAnterior"));
        assertEquals("NUEVO", captor.getValue().datos().get("estado"));
    }

    @Test
    @DisplayName("la clave distingue un evento de otro del mismo ticket")
    void clavePorTipoYTicket() {
        // Un ticket que se crea y se cierra en el mismo minuto son dos hechos. Con
        // una clave que fuera solo el id del ticket, el proceso de cierre no se
        // ejecutaría nunca.
        publisher.ticketCreado(ticket());
        publisher.ticketCerrado(ticket());

        org.mockito.ArgumentCaptor<WorkflowEvento> captor = org.mockito.ArgumentCaptor.forClass(WorkflowEvento.class);
        verify(triggerService, org.mockito.Mockito.times(2)).disparar(captor.capture());

        java.util.List<String> claves = captor.getAllValues().stream().map(WorkflowEvento::clave).toList();
        assertTrue(claves.get(0).contains("TICKET_CREATED"), claves.toString());
        assertTrue(claves.get(1).contains("TICKET_CLOSED"), claves.toString());
        assertFalse(claves.get(0).equals(claves.get(1)));
    }

    @Test
    @DisplayName("un fallo al encolar no se propaga al cambio que ya se hizo")
    void falloAlEncolarNoSePropaga() {
        // El ticket ya está creado y confirmado. Que la automatización no arranque
        // no puede ser motivo para deshacer el ticket ni para devolver un error al
        // solicitante que sí se creó su ticket.
        org.mockito.Mockito.doThrow(new IllegalStateException("motor caído"))
                .when(triggerService).disparar(any(WorkflowEvento.class));

        // No debe lanzar. Si el publicador dejara subir la excepción, el error
        // aparecería en la creación del ticket y el usuario creería que falló.
        publisher.ticketCreado(ticket());
        verify(triggerService).disparar(tipo("TICKET_CREATED"));
    }

    /** Coincide con un evento de ese tipo. */
    private static WorkflowEvento tipo(String tipo) {
        return org.mockito.ArgumentMatchers.argThat(ev -> ev != null && tipo.equals(ev.tipo()));
    }

    // ============================ Fase 2 ============================

    @Test
    @DisplayName("cerrar un ticket publica el cambio de estado Y el evento de cierre")
    void cierrePublicaSuEvento() {
        Ticket t = ticket();
        t.getStatusTicket().setNameStatus("CERRADO");

        publisher.ticketCambioEstado(t, "RESUELTO");

        // Antes existía ticketCerrado() pero nadie lo llamaba: un workflow con
        // disparador "Ticket cerrado" no se ejecutaba nunca.
        verify(triggerService).disparar(tipo("TICKET_STATUS_CHANGED"));
        verify(triggerService).disparar(tipo("TICKET_CLOSED"));
    }

    @Test
    @DisplayName("dos actualizaciones del mismo ticket son dos hechos distintos")
    void dosActualizacionesNoSeDeduplican() {
        publisher.ticketActualizado(ticket());
        publisher.ticketActualizado(ticket());

        org.mockito.ArgumentCaptor<WorkflowEvento> captor = org.mockito.ArgumentCaptor.forClass(WorkflowEvento.class);
        verify(triggerService, org.mockito.Mockito.times(2)).disparar(captor.capture());
        // Con la clave TICKET_UPDATED:<id>, la segunda se descartaba durante 24 h.
        assertFalse(captor.getAllValues().get(0).clave().equals(captor.getAllValues().get(1).clave()));
    }

    @Test
    @DisplayName("un evento provocado por un workflow lleva su ejecución de origen")
    void llevaElOrigen() {
        WorkflowOrigen.establecer(id(500), id(10), 2);
        try {
            publisher.ticketActualizado(ticket());
        } finally {
            WorkflowOrigen.limpiar();
        }

        org.mockito.ArgumentCaptor<WorkflowEvento> captor = org.mockito.ArgumentCaptor.forClass(WorkflowEvento.class);
        verify(triggerService).disparar(captor.capture());
        assertEquals(id(500), captor.getValue().origen().ejecucionId());
        assertEquals(2, captor.getValue().origen().profundidad());
        assertEquals("TICKET", captor.getValue().entidadTipo());
        assertEquals(id(77), captor.getValue().entidadId());
    }

    @Test
    @DisplayName("el cambio de prioridad publica su evento y el de actualización")
    void prioridadPublicaSuEvento() {
        publisher.ticketCambioPrioridad(ticket(), "BAJA");

        verify(triggerService).disparar(tipo("TICKET_PRIORITY_CHANGED"));
        verify(triggerService).disparar(tipo("TICKET_UPDATED"));
    }
}
