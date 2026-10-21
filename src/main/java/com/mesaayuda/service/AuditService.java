package com.mesaayuda.service;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketAudit;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.TicketAuditRepository;
import com.mesaayuda.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    @Autowired
    private TicketAuditRepository auditRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAccessPolicy accessPolicy;

    /** Entradas cuyo valor es el texto de un mensaje (posible nota interna). */
    static final java.util.Set<String> ACCIONES_DE_MENSAJE =
            java.util.Set.of("ANSWER", "ANSWER_EDITED", "ANSWER_DELETED");

    @Transactional
    public void record(Ticket ticket, String actionType, String oldValue, String newValue, String metadata) {
        TicketAudit audit = new TicketAudit();
        audit.setTicket(ticket);
        audit.setActionType(actionType);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setMetadata(metadata);
        try {
            User current = userService.getCurrentUser();
            audit.setUser(current);
        } catch (Exception ignored) {
            // contexto ausente (procesos programados)
        }
        auditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.mesaayuda.dto.AuditLogResponse> findByTicket(UUID ticketId) {
        // Antes no comprobaba nada: el historial de un ticket ajeno era legible
        // con solo tener sesion, escribiendo el id en la URL. El historial no es
        // inocuo: guarda los valores anterior y nuevo de cada cambio de estado
        // y de agente, que es informacion operativa de la institucion.
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> !t.getDeletedTicket())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", ticketId));
        accessPolicy.exigirLectura(ticket, "ver la auditoría", ticketId);
        // El solicitante puede leer su ticket, pero el historial guarda el texto
        // de cada mensaje, también de las notas internas: para quien no es
        // personal se quitan las entradas de mensajes.
        boolean personal = accessPolicy.esPersonal(userService.getCurrentUser());

        return auditRepository.findByTicketIdTicketOrderByCreatedAtAuditDesc(ticketId).stream()
                .filter(a -> personal || !ACCIONES_DE_MENSAJE.contains(a.getActionType()))
                .map(a -> new com.mesaayuda.dto.AuditLogResponse(
                        a.getIdAudit(),
                        a.getActionType(),
                        a.getUser() != null ? a.getUser().getCorporateEmailUser() : "SISTEMA",
                        a.getOldValue(),
                        a.getNewValue(),
                        a.getCreatedAtAudit()))
                .toList();
    }
}