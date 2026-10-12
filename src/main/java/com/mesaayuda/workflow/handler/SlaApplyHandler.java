package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.service.SlaService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Aplica la política de SLA al ticket.
 *
 * <p>Delega en {@code SlaService.applySlaToTicket}, que ya arranca los relojes de
 * respuesta y resolución. Un workflow puede así poner un SLA a un ticket que entró
 * por un canal distinto del habitual.
 */
@Component
public class SlaApplyHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final SlaService slaService;

    public SlaApplyHandler(SlaService slaService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.slaService = slaService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.SLA_APPLY;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }

        // Sin política: se recalcula por las reglas (útil tras cambiar la
        // prioridad). Con política: se fuerza esa ("cambiar SLA").
        UUID politicaId = politicaId(contexto, nodo);
        var sla = politicaId == null
                ? slaService.applySlaToTicket(ticket)
                : slaService.aplicarPolitica(ticket, politicaId);
        contexto.resultado(nodo, "politica", sla.getSlaPolicy() == null ? null : sla.getSlaPolicy().getNameSlaPolicy());
        contexto.resultado(nodo, "venceResolucion",
                sla.getResolutionDueAt() == null ? null : sla.getResolutionDueAt().toString());
        return WorkflowResult.avanzar();
    }

    /** Política concreta indicada en el nodo, o null si no hay o no es un UUID válido. */
    private UUID politicaId(WorkflowContext contexto, WorkflowNodeDef nodo) {
        String texto = contexto.configTexto(nodo, "politicaId");
        if (texto == null) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}