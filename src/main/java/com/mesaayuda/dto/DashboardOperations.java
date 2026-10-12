package com.mesaayuda.dto;

import java.util.List;

public record DashboardOperations(
        long openTickets,
        long unassignedTickets,
        long criticalTickets,
        long slaAtRisk,
        long slaBreachedOpen,
        long createdToday,
        long resolvedToday,
        List<TicketEvent> recentActivity) {
}