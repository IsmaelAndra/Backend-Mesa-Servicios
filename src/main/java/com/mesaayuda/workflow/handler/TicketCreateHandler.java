package com.mesaayuda.workflow.handler;

import com.mesaayuda.dto.TicketRequest;
import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.service.TicketService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

/**
 * Crea un ticket nuevo.
 *
 * <p>Delega en {@link TicketService#create}: el ticket nace con la misma
 * validación, el mismo enrutamiento, el mismo SLA y las mismas notificaciones que
 * uno creado desde la pantalla. El solicitante es el autor del workflow, porque
 * es con su identidad con la que actúa el motor.
 *
 * <p>Config: {@code titulo} y {@code descripcion} (con variables), y opcionalmente
 * {@code categoriaId}, {@code prioridadId}, {@code tipoId}, {@code sedeId}. El id del
 * ticket creado queda en {@code nodos.<id>.ticketId}.
 */
@Component
public class TicketCreateHandler implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketCreateHandler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_CREATE;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        String titulo = contexto.configTexto(nodo, "titulo");
        String descripcion = contexto.configTexto(nodo, "descripcion");
        if (titulo == null || descripcion == null) {
            return WorkflowResult.fallar("El nodo TICKET_CREATE '" + nodo.id() + "' necesita título y descripción.");
        }
        if (titulo.length() > 255) {
            titulo = titulo.substring(0, 255);
        }
        TicketResponse creado = ticketService.create(new TicketRequest(titulo, descripcion,
                contexto.configUuid(nodo, "categoriaId"),
                contexto.configUuid(nodo, "prioridadId"),
                contexto.configUuid(nodo, "tipoId"),
                contexto.configUuid(nodo, "sedeId")));
        contexto.resultado(nodo, "ticketId", creado == null ? null : creado.idTicket());
        // El número es lo que se lee ("#43"); el id sirve para encadenar nodos.
        contexto.resultado(nodo, "ticketNumero", creado == null ? null : creado.numberTicket());
        return WorkflowResult.avanzar();
    }

}
