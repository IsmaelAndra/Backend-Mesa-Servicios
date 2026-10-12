package com.mesaayuda.workflow.handler;

import com.mesaayuda.dto.AnswerRequest;
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
 * Agrega un comentario al ticket.
 *
 * <p>El texto puede traer {@code [variable]} y se sustituye por lo que haya en el
 * contexto. Es lo mismo que hacen las plantillas de notificación, y por el mismo
 * motivo: quien escribe el workflow lee texto, no código.
 */
@Component
public class TicketCommentHandler extends TicketActionSupport implements WorkflowNodeHandler {

    private final TicketService ticketService;

    public TicketCommentHandler(TicketService ticketService, TicketRepository ticketRepository) {
        super(ticketRepository);
        this.ticketService = ticketService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TICKET_COMMENT;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Ticket ticket = ticketDe(contexto, nodo);
        if (ticket == null) {
            return sinTicket(nodo, idTicket(contexto, nodo));
        }

        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        Object bruto = config.get("mensaje");
        if (bruto == null || String.valueOf(bruto).isBlank()) {
            return WorkflowResult.fallar("El nodo TICKET_COMMENT '" + nodo.id() + "' no tiene mensaje.");
        }

        boolean interno = Boolean.TRUE.equals(config.get("interno"));
        // [variable] se mantiene por compatibilidad (es la sintaxis de las
        // plantillas de notificación); {{variable}} es la del constructor.
        String mensaje = contexto.interpolar(rellenar(String.valueOf(bruto), contexto));

        var respuesta = ticketService.addAnswer(ticket.getIdTicket(), new AnswerRequest(mensaje, interno));
        contexto.resultado(nodo, "respuestaId", respuesta == null ? null : respuesta.idAnswer());
        return WorkflowResult.avanzar();
    }

    /**
     * Sustituye {@code [variable]} por su valor.
     *
     * <p>Una variable que no existe se deja tal cual. Es mejor ver
     * {@code [agente]} en el comentario que ver un hueco sin explicación: quien
     * lo lea sabrá que el dato no llegó, en vez de preguntarse qué pasó con el
     * proceso.
     */
    private String rellenar(String texto, WorkflowContext contexto) {
        StringBuilder salida = new StringBuilder();
        int i = 0;
        while (i < texto.length()) {
            int inicio = texto.indexOf('[', i);
            if (inicio < 0) {
                salida.append(texto, i, texto.length());
                break;
            }
            int fin = texto.indexOf(']', inicio);
            if (fin < 0) {
                salida.append(texto, i, texto.length());
                break;
            }
            String nombre = texto.substring(inicio + 1, fin);
            Object valor = contexto.leer(nombre);
            salida.append(texto, i, inicio);
            salida.append(valor == null ? texto.substring(inicio, fin + 1) : String.valueOf(valor));
            i = fin + 1;
        }
        return salida.toString();
    }
}