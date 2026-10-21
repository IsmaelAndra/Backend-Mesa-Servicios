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

import java.util.Map;

/**
 * Cambia la prioridad del ticket.
 *
 * <p>Delega en {@code TicketService.changePriority} para que el cambio quede
 * auditado y se emita el SSE. Escribir la prioridad directamente desde el nodo
 * sería más corto, pero dejaría la pantalla de los agentes mostrando un valor que
 * ya no es el real.
 */
@Component
public class TicketPriorityHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketPriorityHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_PRIORITY;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }

        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        // Admite variable, p. ej. {{nodos.nexa1.prioridadId}} (la que sugirió NEXA).
        java.util.UUID prioridadIdCfg = contexto.configUuid(nodo, "prioridadId");
        java.util.UUID prioridadId = null;
        if (prioridadIdCfg != null) {
            prioridadId = prioridadIdCfg;
        }
        if (prioridadId == null) {
            prioridadId = uuid(null, contexto.leer("prioridadId"));
        }
        if (prioridadId == null) {
            return WorkflowResult.fallar("El nodo TICKET_PRIORITY '" + nodo.id()
                    + "' no indica la prioridad de destino.");
        }

        // La prioridad que eligió una persona no la pisa una automatización
        // (salvo "forzar"): de ella depende el plazo del SLA.
        if (Boolean.TRUE.equals(ticket.getPriorityManualTicket())
                && !Boolean.TRUE.equals(config.get("forzar"))) {
            contexto.resultado(nodo, "omitido", "La prioridad la eligió el usuario; no se cambia.");
            return WorkflowResult.avanzar();
        }

        ticketService.changePriority(ticket.getIdTicket(), prioridadId);
        return WorkflowResult.avanzar();
    }

    private java.util.UUID uuid(Object valor, Object alternativa) {
        Object[] candidatos = {valor, alternativa};
        for (Object c : candidatos) {
            if (c == null) {
                continue;
            }
            if (c instanceof java.util.UUID u) {
                return u;
            }
            try {
                return java.util.UUID.fromString(String.valueOf(c).trim());
            } catch (Exception e) {
                // Se prueba el siguiente.
            }
        }
        return null;
    }
}