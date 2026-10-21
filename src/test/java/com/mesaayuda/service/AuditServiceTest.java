package com.mesaayuda.service;

import com.mesaayuda.IdsPrueba;
import com.mesaayuda.dto.AuditLogResponse;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketAudit;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketAuditRepository;
import com.mesaayuda.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** El historial de un ticket no debe enseñar al solicitante el texto de las notas internas. */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private TicketAuditRepository auditRepository;
    @Mock private UserService userService;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketAccessPolicy accessPolicy;
    @InjectMocks private AuditService auditService;

    private final UUID ticketId = IdsPrueba.id(1);
    private final User actual = new User();

    private void preparar(boolean personal) {
        Ticket ticket = new Ticket();
        ticket.setIdTicket(ticketId);
        ticket.setDeletedTicket(false);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userService.getCurrentUser()).thenReturn(actual);
        when(accessPolicy.esPersonal(actual)).thenReturn(personal);
        when(auditRepository.findByTicketIdTicketOrderByCreatedAtAuditDesc(ticketId)).thenReturn(List.of(
                entrada("STATUS_CHANGE", "EN_PROCESO"),
                entrada("ANSWER", "Nota interna: el usuario no paga la licencia"),
                entrada("ANSWER_EDITED", "texto anterior")));
    }

    private static TicketAudit entrada(String accion, String valor) {
        TicketAudit a = new TicketAudit();
        a.setActionType(accion);
        a.setNewValue(valor);
        return a;
    }

    @Test
    void elSolicitanteNoVeLosMensajes() {
        preparar(false);
        List<AuditLogResponse> historial = auditService.findByTicket(ticketId);
        assertEquals(1, historial.size());
        assertEquals("STATUS_CHANGE", historial.get(0).actionType());
    }

    @Test
    void elPersonalVeTodoElHistorial() {
        preparar(true);
        assertEquals(3, auditService.findByTicket(ticketId).size());
    }
}
