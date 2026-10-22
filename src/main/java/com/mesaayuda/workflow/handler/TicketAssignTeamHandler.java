package com.mesaayuda.workflow.handler;

import com.mesaayuda.dto.TicketResponse;
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
 * Pasa el ticket a un equipo; el agente lo elige el método del propio equipo.
 *
 * <p>Config: {@code equipoId} (número o variable, p. ej. {@code {{nodos.mapi1.equipoId}}}).
 * Delega en {@link TicketService#assignTeam}, que audita, notifica y publica los
 * mismos eventos que una asignación hecha a mano.
 */
@Component
public class TicketAssignTeamHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketAssignTeamHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_ASSIGN_TEAM;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }
        java.util.UUID equipoIdCfg = contexto.configUuid(nodo, "equipoId");
        java.util.UUID equipoId = null;
        if (equipoIdCfg != null) {
            equipoId = equipoIdCfg;
        }
        if (equipoId == null) {
            return WorkflowResult.fallar("El nodo '" + nodo.id() + "' no indica el equipo (equipoId).");
        }
        TicketResponse r = ticketService.assignTeam(ticket.getIdTicket(), equipoId);
        contexto.resultado(nodo, "equipoId", equipoId);
        contexto.resultado(nodo, "agente", r == null ? null : r.agent());
        return WorkflowResult.avanzar();
    }
}
