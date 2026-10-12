package com.mesaayuda.workflow;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Puente entre los servicios del sistema y el motor de workflows.
 *
 * <p>Los servicios avisan de un hecho (ticket creado, SLA incumplido, usuario
 * desactivado) y aquí se convierte en un {@link WorkflowEvento}. El encolado va
 * en {@code afterCommit}: si el cambio se revierte, ningún workflow llega a
 * enterarse de algo que no pasó.
 *
 * <p><b>Origen.</b> El {@link WorkflowOrigen} se lee al publicar, no en el
 * afterCommit: en ese instante el hilo todavía está dentro del nodo que provocó
 * el cambio, y es lo que permite saber que el evento lo causó un workflow.
 *
 * <p><b>Clave única por hecho.</b> Antes la clave era {@code TICKET_UPDATED:<id>}
 * y la deduplicación la guarda 24 horas, así que la segunda actualización de un
 * ticket en el mismo día no disparaba nada. Ahora cada hecho lleva su propio
 * identificador; los bucles los corta la profundidad, no la deduplicación.
 */
@Component
public class WorkflowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventPublisher.class);

    private final WorkflowTriggerService triggerService;

    public WorkflowEventPublisher(WorkflowTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    // ============================ Tickets ============================

    public void ticketCreado(Ticket ticket) {
        publicarTicket(WorkflowTriggerType.TICKET_CREATED, ticket, datos(ticket));
    }

    /**
     * Cambio de estado. Además del evento genérico, el cierre y la reapertura
     * tienen el suyo: antes existían los métodos pero nadie los llamaba, así que
     * un workflow con disparador "Ticket cerrado" no se ejecutaba nunca.
     */
    public void ticketCambioEstado(Ticket ticket, String estadoAnterior) {
        Map<String, Object> datos = datos(ticket);
        datos.put("estadoAnterior", estadoAnterior);
        publicarTicket(WorkflowTriggerType.TICKET_STATUS_CHANGED, ticket, datos);
        String nuevo = estado(ticket);
        if ("CERRADO".equals(nuevo)) {
            publicarTicket(WorkflowTriggerType.TICKET_CLOSED, ticket, new LinkedHashMap<>(datos));
        } else if ("REABIERTO".equals(nuevo)) {
            publicarTicket(WorkflowTriggerType.TICKET_REOPENED, ticket, new LinkedHashMap<>(datos));
        }
    }

    public void ticketAsignado(Ticket ticket) {
        publicarTicket(WorkflowTriggerType.TICKET_ASSIGNED, ticket, datos(ticket));
    }

    public void ticketCerrado(Ticket ticket) {
        publicarTicket(WorkflowTriggerType.TICKET_CLOSED, ticket, datos(ticket));
    }

    public void ticketReabierto(Ticket ticket) {
        publicarTicket(WorkflowTriggerType.TICKET_REOPENED, ticket, datos(ticket));
    }

    public void ticketActualizado(Ticket ticket) {
        publicarTicket(WorkflowTriggerType.TICKET_UPDATED, ticket, datos(ticket));
    }

    /** Cambio de prioridad: su evento propio y el genérico de actualización. */
    public void ticketCambioPrioridad(Ticket ticket, String prioridadAnterior) {
        Map<String, Object> datos = datos(ticket);
        datos.put("prioridadAnterior", prioridadAnterior);
        publicarTicket(WorkflowTriggerType.TICKET_PRIORITY_CHANGED, ticket, datos);
        publicarTicket(WorkflowTriggerType.TICKET_UPDATED, ticket, new LinkedHashMap<>(datos));
    }

    /** Cambio de categoría: su evento propio y el genérico de actualización. */
    public void ticketCambioCategoria(Ticket ticket, String categoriaAnterior) {
        Map<String, Object> datos = datos(ticket);
        datos.put("categoriaAnterior", categoriaAnterior);
        publicarTicket(WorkflowTriggerType.TICKET_CATEGORY_CHANGED, ticket, datos);
        publicarTicket(WorkflowTriggerType.TICKET_UPDATED, ticket, new LinkedHashMap<>(datos));
    }

    // ============================ SLA ============================

    public void slaCercano(TicketSla sla, int porcentaje) {
        if (sla == null || sla.getTicket() == null) {
            return;
        }
        Map<String, Object> datos = datos(sla.getTicket());
        datos.put("slaPorcentaje", porcentaje);
        datos.put("slaVence", sla.getResponseDueAt() == null ? null : sla.getResponseDueAt().toString());
        publicarTicket(WorkflowTriggerType.SLA_WARNING, sla.getTicket(), datos);
    }

    public void slaIncumplido(TicketSla sla) {
        if (sla == null || sla.getTicket() == null) {
            return;
        }
        Map<String, Object> datos = datos(sla.getTicket());
        datos.put("slaVence", sla.getResponseDueAt() == null ? null : sla.getResponseDueAt().toString());
        publicarTicket(WorkflowTriggerType.SLA_BREACHED, sla.getTicket(), datos);
    }

    // ============================ Usuarios ============================

    public void usuarioCreado(User usuario) {
        publicarUsuario(WorkflowTriggerType.USER_CREATED, usuario, Map.of());
    }

    public void usuarioActualizado(User usuario) {
        publicarUsuario(WorkflowTriggerType.USER_UPDATED, usuario, Map.of());
    }

    public void usuarioDesactivado(User usuario, String estadoAnterior) {
        publicarUsuario(WorkflowTriggerType.USER_DEACTIVATED, usuario,
                Map.of("estadoAnterior", estadoAnterior == null ? "" : estadoAnterior));
    }

    // ============================ Construcción ============================

    private void publicarTicket(WorkflowTriggerType tipo, Ticket ticket, Map<String, Object> datos) {
        publicar(tipo, "TICKET", ticket.getIdTicket(), datos);
    }

    private void publicarUsuario(WorkflowTriggerType tipo, User usuario, Map<String, Object> extra) {
        if (usuario == null) {
            return;
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("usuarioId", usuario.getIdUser());
        datos.put("correo", usuario.getCorporateEmailUser());
        datos.put("rol", usuario.getRolUser() == null ? null : usuario.getRolUser().getNameRole());
        datos.put("estado", usuario.getStatusUser());
        datos.putAll(extra);
        publicar(tipo, "USUARIO", usuario.getIdUser(), datos);
    }

    /** Foto del ticket en el momento del hecho. Lo vivo se lee con {@code ticket.*}. */
    private Map<String, Object> datos(Ticket ticket) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ticketId", ticket.getIdTicket());
        datos.put("titulo", ticket.getTitleTicket());
        datos.put("descripcion", ticket.getDescriptionTicket());
        datos.put("estado", estado(ticket));
        datos.put("prioridad", ticket.getPriorityTicket() == null
                ? null : ticket.getPriorityTicket().getNamePriority());
        datos.put("categoria", ticket.getCategoryTicket() == null
                ? null : ticket.getCategoryTicket().getNameCategory());
        datos.put("area", ticket.getAreaTicket() == null
                ? null : ticket.getAreaTicket().getNameArea());
        datos.put("solicitanteId", ticket.getUserTicket() == null
                ? null : ticket.getUserTicket().getIdUser());
        datos.put("solicitanteCorreo", ticket.getUserTicket() == null
                ? null : ticket.getUserTicket().getCorporateEmailUser());
        datos.put("agenteId", ticket.getAgentTicket() == null
                ? null : ticket.getAgentTicket().getIdUser());
        return datos;
    }

    private String estado(Ticket ticket) {
        return ticket.getStatusTicket() == null ? null : ticket.getStatusTicket().getNameStatus();
    }

    private void publicar(WorkflowTriggerType tipo, String entidadTipo, UUID entidadId, Map<String, Object> datos) {
        String clave = tipo.name() + ":" + entidadId + ":" + UUID.randomUUID().toString().substring(0, 13);
        // Se captura ahora: en el afterCommit ya no estamos dentro del nodo.
        WorkflowOrigen.Datos origen = WorkflowOrigen.actual();
        WorkflowEvento evento = new WorkflowEvento(tipo.name(), clave, datos, entidadTipo, entidadId, origen);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            disparar(evento);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                disparar(evento);
            }
        });
    }

    private void disparar(WorkflowEvento evento) {
        try {
            triggerService.disparar(evento);
        } catch (RuntimeException e) {
            // El cambio del sistema ya está confirmado y no se va a deshacer por
            // culpa de una automatización. La excepción se avisa, pero no sube.
            log.error("No se pudo encolar el evento de workflow {} ({}): {}",
                    evento.tipo(), evento.clave(), e.getMessage(), e);
        }
    }
}
