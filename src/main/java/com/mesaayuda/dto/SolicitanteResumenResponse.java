package com.mesaayuda.dto;

import java.util.List;
import java.util.Map;

public record SolicitanteResumenResponse(
        UserResponse solicitante,
        List<TicketResponse> historial,
        long totalTickets,
        long openTickets,
        long resolvedTickets,
        Double avgSatisfaction,
        Double avgResolutionHours,
        Map<String, Long> ticketsPorEstado,
        List<Long> ticketsUltimos6Meses
) {}