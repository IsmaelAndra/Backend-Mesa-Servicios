package com.mesaayuda.service;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketAudit;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    @Autowired
    private TicketAuditRepository auditRepository;

    @Autowired
    private UserService userService;

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
    public java.util.List<com.mesaayuda.dto.AuditLogResponse> findByTicket(Long ticketId) {
        return auditRepository.findByTicketIdTicketOrderByCreatedAtAuditDesc(ticketId).stream()
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