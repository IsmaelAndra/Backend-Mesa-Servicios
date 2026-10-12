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

import java.util.Map;
import java.util.Set;

/**
 * Cambia el estado del ticket.
 *
 * <p>Los estados posibles son un conjunto cerrado del catálogo
 * ({@code status}), no texto libre. Se comprueba aquí porque un estado mal
 * escrito llegaría hasta la base y el ticket se quedaría en un valor que ninguna
 * consulta del panel recognise, es decir, invisible.
 */
@Component
public class TicketStatusHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private static final Set<String> ESTADOS = Set.of(
            "NUEVO", "ASIGNADO", "EN_PROCESO", "RESUELTO", "CERRADO", "ESCALADO", "REABIERTO");

    private final TicketService ticketService;

    public TicketStatusHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_STATUS;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }

        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        String estado = contexto.configTexto(nodo, "estado");
        if (estado == null || estado.isEmpty()) {
            return WorkflowResult.fallar("El nodo TICKET_STATUS '" + nodo.id() + "' no dice a que estado pasa.");
        }

        String normalizado = estado.toUpperCase(java.util.Locale.ROOT);
        if (!ESTADOS.contains(normalizado)) {
            return WorkflowResult.fallar("El estado '" + estado + "' no existe. Validos: " + ESTADOS);
        }

        ticketService.changeStatus(ticket.getIdTicket(), new StatusChangeRequest(normalizado));
        contexto.escribir("ultimoEstadoTicket", normalizado);
        return WorkflowResult.avanzar();
    }
}