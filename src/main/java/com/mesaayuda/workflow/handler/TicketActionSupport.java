package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowResult;

import java.util.Map;

/**
 * Lo que tienen en común los nodos que actúan sobre un ticket.
 *
 * <p>Todos necesitan el mismo paso previo: averiguar de qué ticket hablan. Ese
 * paso se centraliza aquí porque es donde se decide de dónde sale el id, y esa
 * decisión tiene que ser igual para todos. Si cada handler aceptara un
 * {@code ticketId} a su manera, un workflow podria referirse al ticket
 * equivocado según el nodo, y eso no se detecta hasta que se mueve un ticket que
 * no debía moverse.
 *
 * <p>El id se busca, por orden, en: la configuración del nodo, la variable
 * {@code ticketId} del contexto y {@code ticket.id} del payload del disparador.
 */
abstract class TicketActionSupport {

    protected final TicketRepository ticketRepository;

    protected TicketActionSupport(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Resultado listo para devolver cuando no hay ticket o no existe.
     *
     * <p>Falla en vez de continuar. Un nodo de ticket sin ticket no tiene un
     * comportamiento razonable: lo más probable es que se haya escrito mal la
     * variable, y seguir dejaría el proceso haciendo cosas sin sentido.
     */
    protected WorkflowResult sinTicket(WorkflowNodeDef nodo, java.util.UUID id) {
        return WorkflowResult.fallar("El nodo '" + nodo.id() + "' necesita un ticket y "
                + (id == null ? "no se indica ninguno" : "el ticket " + id + " no existe"));
    }

    protected Ticket ticketDe(WorkflowContext contexto, WorkflowNodeDef nodo) {
        java.util.UUID id = idTicket(contexto, nodo);
        if (id == null) {
            return null;
        }
        return ticketRepository.findById(id).orElse(null);
    }

    protected java.util.UUID idTicket(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        return primeroUuid(config.get("ticketId"),
                contexto.leer("ticketId"),
                contexto.leer("ticket.id"),
                contexto.leer("idTicket"));
    }

    private java.util.UUID primeroUuid(Object... candidatos) {
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
                // Siguiente candidato: un valor no numérico aquí es un dato mal
                // formado, no un motivo para tirar la ejecución entera.
            }
        }
        return null;
    }
}