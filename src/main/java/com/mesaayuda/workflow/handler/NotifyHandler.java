package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.NotificationService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Notificación interna (campana y, si el correo está activo, también correo).
 *
 * <p>Delega en {@link NotificationService}: el formato, la plantilla y el aviso por
 * SSE son los mismos que los de cualquier notificación del sistema. Escribirla
 * aquí "por lo rápido" significaría una segunda forma de notificar.
 *
 * <p>Destinatarios, uno de:
 * <ul>
 *   <li>{@code destino}: SOLICITANTE o AGENTE del ticket.</li>
 *   <li>{@code usuarioId} o {@code correo}: una persona (admiten variables).</li>
 *   <li>{@code rol}: todas las cuentas activas de ese rol.</li>
 * </ul>
 *
 * <p>Antes exigía un ticket y fallaba sin él; ahora, sin ticket, la notificación
 * se crea sin enlace (avisos de un workflow programado o de usuarios).
 */
@Component
public class NotifyHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyHandler.class);

    /** Tipo con el que se guarda la notificación cuando no cuelga de un ticket. */
    static final String TIPO_SIN_TICKET = "WORKFLOW";

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    public NotifyHandler(NotificationService notificationService, UserRepository userRepository,
                         TicketRepository ticketRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.NOTIFY;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        String mensaje = contexto.configTexto(nodo, "mensaje");
        if (mensaje == null) {
            return WorkflowResult.fallar("El nodo NOTIFY '" + nodo.id() + "' no tiene mensaje.");
        }
        Ticket ticket = ticketOpcional(contexto, nodo);
        List<User> destinatarios = destinatarios(contexto, nodo, ticket);
        if (destinatarios.isEmpty()) {
            return WorkflowResult.fallar("El nodo NOTIFY '" + nodo.id()
                    + "' no tiene destinatario, o el indicado no existe o no está activo.");
        }
        String titulo = contexto.configTexto(nodo, "titulo");
        int avisados = 0;
        for (User destino : destinatarios) {
            try {
                if (ticket != null) {
                    notificationService.notifyTicketUpdated(ticket, destino, mensaje);
                } else {
                    notificationService.sendNotification(null, destino, TIPO_SIN_TICKET,
                            titulo == null ? "Aviso de la mesa de servicios" : titulo, mensaje);
                }
                avisados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar a {} desde el nodo NOTIFY '{}': {}",
                        destino.getCorporateEmailUser(), nodo.id(), e.getMessage());
            }
        }
        if (avisados == 0) {
            return WorkflowResult.fallar("El nodo NOTIFY '" + nodo.id() + "' no pudo avisar a nadie.");
        }
        contexto.resultado(nodo, "avisados", avisados);
        log.info("El nodo NOTIFY '{}' avisó a {} persona(s)", nodo.id(), avisados);
        return WorkflowResult.avanzar();
    }

    private List<User> destinatarios(WorkflowContext contexto, WorkflowNodeDef nodo, Ticket ticket) {
        List<User> lista = new ArrayList<>();
        String destino = contexto.configTexto(nodo, "destino");
        if (destino != null && ticket != null) {
            switch (destino.toUpperCase(Locale.ROOT)) {
                case "SOLICITANTE" -> agregar(lista, ticket.getUserTicket());
                case "AGENTE" -> agregar(lista, ticket.getAgentTicket());
                default -> { }
            }
        }
        java.util.UUID usuarioIdCfg = contexto.configUuid(nodo, "usuarioId");
        if (usuarioIdCfg != null) {
            java.util.UUID usuarioId = usuarioIdCfg;
            userRepository.findById(usuarioId).ifPresent(u -> agregar(lista, u));
        }
        String correo = contexto.configTexto(nodo, "correo");
        if (correo != null) {
            userRepository.findByCorporateEmailUser(correo).ifPresent(u -> agregar(lista, u));
        }
        String rol = contexto.configTexto(nodo, "rol");
        if (rol != null) {
            userRepository.findByRoleName(rol.toUpperCase(Locale.ROOT)).forEach(u -> agregar(lista, u));
        }
        // Sin repetidos: alguien puede ser a la vez el agente y miembro del rol.
        Map<java.util.UUID, User> unicos = new LinkedHashMap<>();
        lista.forEach(u -> unicos.putIfAbsent(u.getIdUser(), u));
        return new ArrayList<>(unicos.values());
    }

    /** Solo cuentas activas: avisar a alguien dado de baja es avisar a nadie. */
    private void agregar(List<User> lista, User u) {
        if (u != null && !Boolean.TRUE.equals(u.getDeletedUser()) && "ACTIVE".equalsIgnoreCase(u.getStatusUser())) {
            lista.add(u);
        }
    }

    private Ticket ticketOpcional(WorkflowContext contexto, WorkflowNodeDef nodo) {
        java.util.UUID idCfg = contexto.configUuid(nodo, "ticketId");
        java.util.UUID id = null;
        if (idCfg != null) {
            id = idCfg;
        } else {
            Object bruto = contexto.leer("ticketId");
            if (bruto instanceof java.util.UUID u) {
                id = u;
            } else if (bruto != null) {
                try {
                    String s = String.valueOf(bruto).trim();
                    id = java.util.UUID.fromString(s);
                } catch (Exception e) {
                    id = null;
                }
            }
        }
        return id == null ? null : ticketRepository.findById(id).orElse(null);
    }
}
