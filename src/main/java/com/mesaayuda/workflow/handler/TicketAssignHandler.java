package com.mesaayuda.workflow.handler;

import com.mesaayuda.dto.AssignRequest;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.service.TicketService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Asigna agente o equipo.
 *
 * <p>Delega en {@code TicketService.assign} y no manda nada por su cuenta. Ese
 * servicio ya notifica, audita y emite SSE; si el nodo volviera a notificar, el
 * agente recibiría dos correos idénticos y la auditoría tendría dos entradas por
 * una sola acción.
 */
@Component
public class TicketAssignHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketAssignHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_ASSIGN;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }

        // Solo lo que dice la configuración (admite variables). Antes, si faltaba,
        // se tomaba "agenteId" del evento, que es el agente ACTUAL del ticket: un
        // nodo configurado solo con equipo reasignaba el ticket a quien ya lo tenía.
        java.util.UUID agenteId = contexto.configUuid(nodo, "agenteId");
        java.util.UUID equipoId = contexto.configUuid(nodo, "equipoId");

        if (agenteId == null && equipoId == null) {
            return WorkflowResult.fallar("El nodo TICKET_ASSIGN '" + nodo.id()
                    + "' no indica ni agente ni equipo.");
        }
        if (agenteId == null) {
            // Grafos anteriores al nodo TICKET_ASSIGN_TEAM: assign() ignoraba el
            // equipo y enrutaba por la categoría. Se pasa por el camino correcto.
            ticketService.assignTeam(ticket.getIdTicket(), equipoId);
            contexto.resultado(nodo, "equipoId", equipoId);
            return WorkflowResult.avanzar();
        }

        ticketService.assign(ticket.getIdTicket(), new AssignRequest(agenteId, null));
        contexto.escribir("agenteAsignado", agenteId);
        contexto.resultado(nodo, "agenteId", agenteId);
        return WorkflowResult.avanzar();
    }
}