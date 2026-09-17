package com.mesaayuda.dto;

import java.util.List;
import java.util.Map;

public record DashboardStats(
        long totalTickets,
        long openTickets,
        long resolvedTickets,
        long criticalTickets,
        Double slaComplianceRate,
        Double avgResolutionHours,
        long slaBreachedOpen,
        long slaAtRisk,
        Map<String, Long> ticketsByStatus,
        Map<String, Long> ticketsByArea,
        Map<String, Long> ticketsByCategory,
        List<Integer> ticketsLast12Weeks
) {}