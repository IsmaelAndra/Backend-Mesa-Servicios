package com.mesaayuda.workflow;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.service.TicketService;
import com.mesaayuda.workflow.handler.TicketPriorityHandler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una automatización (MAPI) no pisa la prioridad que eligió una persona: de ella
 * depende el plazo del SLA. Solo la cambia si el nodo dice "forzar".
 */
class TicketPriorityHandlerTest {

    private final TicketService ticketService = mock(TicketService.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final TicketPriorityHandler handler = new TicketPriorityHandler(ticketService, ticketRepository);

    private final UUID ticketId = id(1);
    private final UUID prioridadId = id(2);

    private WorkflowResult ejecutar(boolean manual, Boolean forzar) {
        Ticket ticket = new Ticket();
        ticket.setIdTicket(ticketId);
        ticket.setPriorityManualTicket(manual);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        Map<String, Object> config = new HashMap<>();
        config.put("ticketId", ticketId.toString());
        config.put("prioridadId", prioridadId.toString());
        if (forzar != null) {
            config.put("forzar", forzar);
        }
        WorkflowNodeDef nodo = new WorkflowNodeDef("prioridad", "TICKET_PRIORITY", "Aplicar prioridad", config, 0.0, 0.0);
        return handler.ejecutar(new WorkflowContext(id(10), id(11), id(12), "k", 0), nodo);
    }

    @Test
    void respetaLaPrioridadElegidaPorLaPersona() {
        WorkflowResult r = ejecutar(true, null);
        assertTrue(r.continua());
        verify(ticketService, never()).changePriority(any(), any());
    }

    @Test
    void cambiaLaPrioridadSiNadieLaEligio() {
        ejecutar(false, null);
        verify(ticketService).changePriority(ticketId, prioridadId);
    }

    @Test
    void conForzarLaCambiaAunqueSeaManual() {
        ejecutar(true, true);
        verify(ticketService).changePriority(ticketId, prioridadId);
    }
}
