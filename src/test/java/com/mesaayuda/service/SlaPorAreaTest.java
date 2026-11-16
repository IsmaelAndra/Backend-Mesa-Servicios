package com.mesaayuda.service;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.SlaPolicy;
import com.mesaayuda.entity.SlaRule;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.repository.SlaRuleRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mesa multi-área: el SLA se elige por el área que atiende y, si no tiene regla, por la del solicitante. */
@ExtendWith(MockitoExtension.class)
class SlaPorAreaTest {

    private static final UUID ATIENDE = id(1);
    private static final UUID SOLICITANTE = id(2);
    private static final UUID ALTA = id(3);

    @Mock private SlaRuleRepository slaRuleRepository;
    @Mock private TicketSlaRepository ticketSlaRepository;
    @InjectMocks private SlaService slaService;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        Area atiende = new Area();
        atiende.setIdArea(ATIENDE);
        Area solicitante = new Area();
        solicitante.setIdArea(SOLICITANTE);
        solicitante.setNameArea("Contabilidad");
        ticket.setAreaDestinoTicket(atiende);
        ticket.setAreaTicket(solicitante);
        Priority alta = new Priority();
        alta.setIdPriority(ALTA);
        alta.setNamePriority("ALTA");
        ticket.setPriorityTicket(alta);
        lenient().when(ticketSlaRepository.save(any(TicketSla.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SlaRule regla(String nombre, int respuesta) {
        SlaPolicy p = new SlaPolicy();
        p.setNameSlaPolicy(nombre);
        p.setResponseTimeMinutes(respuesta);
        p.setResolutionTimeMinutes(respuesta * 4);
        SlaRule r = new SlaRule();
        r.setSlaPolicy(p);
        return r;
    }

    @Test
    @DisplayName("manda la regla del área que atiende")
    void mandaElAreaQueAtiende() {
        when(slaRuleRepository.findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(ATIENDE, ALTA))
                .thenReturn(Optional.of(regla("Tecnología alta", 30)));

        TicketSla sla = slaService.applySlaToTicket(ticket);

        assertEquals("Tecnología alta", sla.getSlaPolicy().getNameSlaPolicy());
        verify(slaRuleRepository, never())
                .findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(SOLICITANTE, ALTA);
    }

    @Test
    @DisplayName("sin regla del área que atiende, se usa la del solicitante (como antes)")
    void sinReglaDelAreaSeUsaLaDelSolicitante() {
        when(slaRuleRepository.findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(ATIENDE, ALTA))
                .thenReturn(Optional.empty());
        when(slaRuleRepository.findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(SOLICITANTE, ALTA))
                .thenReturn(Optional.of(regla("Contabilidad alta", 60)));

        assertEquals("Contabilidad alta", slaService.applySlaToTicket(ticket).getSlaPolicy().getNameSlaPolicy());
    }
}
