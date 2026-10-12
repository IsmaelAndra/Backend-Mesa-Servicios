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
 * Cierra el ticket.
 *
 * <p>Azúcar sobre {@code TICKET_STATUS} con el estado fijo {@code CERRADO}. Existe
 * aparte porque "cerrar" es la intención que alguien escribe al construir el
 * grafo, y obligarle a recordar el nombre del estado es una forma de que el
 * proceso se quede abierto por un descuido.
 */
@Component
public class TicketCloseHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private static final String CERRADO = "CERRADO";

    private final TicketService ticketService;

    public TicketCloseHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_CLOSE;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }
        ticketService.changeStatus(ticket.getIdTicket(), new StatusChangeRequest(CERRADO));
        contexto.escribir("ultimoEstadoTicket", CERRADO);
        return WorkflowResult.avanzar();
    }
}