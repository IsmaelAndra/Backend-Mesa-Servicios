package com.mesaayuda.workflow.handler;

import com.mesaayuda.dto.StatusChangeRequest;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.service.TicketService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

/**
 * Reabre el ticket.
 *
 * <p>Igual que cerrar, es azúcar sobre el cambio de estado con el estado fijo.
 */
@Component
public class TicketReopenHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private static final String REABIERTO = "REABIERTO";

    private final TicketService ticketService;

    public TicketReopenHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_REOPEN;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }
        ticketService.changeStatus(ticket.getIdTicket(), new StatusChangeRequest(REABIERTO));
        contexto.escribir("ultimoEstadoTicket", REABIERTO);
        return WorkflowResult.avanzar();
    }
}