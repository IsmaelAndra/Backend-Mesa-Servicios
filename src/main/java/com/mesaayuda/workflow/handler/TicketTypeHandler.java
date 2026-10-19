package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.service.TicketService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cambia el tipo del ticket (INCIDENTE, REQUERIMIENTO, CONSULTA...).
 *
 * <p>Admite una variable, p. ej. {@code {{nodos.nexa.tipoId}}} (el que propuso
 * NEXA). El cambio se delega en el servicio para que quede auditado.
 */
@Component
public class TicketTypeHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketTypeHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_TYPE;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }
        UUID tipoId = contexto.configUuid(nodo, "tipoId");
        if (tipoId == null) {
            Object valor = contexto.leer("tipoId");
            try {
                tipoId = valor == null ? null : UUID.fromString(String.valueOf(valor).trim());
            } catch (IllegalArgumentException e) {
                tipoId = null;
            }
        }
        if (tipoId == null) {
            return WorkflowResult.fallar("El nodo TICKET_TYPE '" + nodo.id() + "' no indica el tipo de destino.");
        }
        ticketService.changeType(ticket.getIdTicket(), tipoId);
        return WorkflowResult.avanzar();
    }
}
