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
 * Cambia la categoría del ticket.
 *
 * <p>La categoría decide a qué equipo va el ticket, así que el cambio se delega en
 * el servicio para que quede auditado y se avise por SSE.
 */
@Component
public class TicketCategoryHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketCategoryHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_CATEGORY;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }

        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        java.util.UUID categoriaIdCfg = contexto.configUuid(nodo, "categoriaId");
        java.util.UUID categoriaId = null;
        if (categoriaIdCfg != null) {
            categoriaId = categoriaIdCfg;
        }
        if (categoriaId == null) {
            categoriaId = uuid(null, contexto.leer("categoriaId"));
        }
        if (categoriaId == null) {
            return WorkflowResult.fallar("El nodo TICKET_CATEGORY '" + nodo.id()
                    + "' no indica la categoría de destino.");
        }

        ticketService.changeCategory(ticket.getIdTicket(), categoriaId);
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