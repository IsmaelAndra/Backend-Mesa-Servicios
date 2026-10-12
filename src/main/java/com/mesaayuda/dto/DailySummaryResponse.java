package com.mesaayuda.dto;

import java.util.List;

public record DailySummaryResponse(
        int sent,
        List<String> recipients,
        long createdToday,
        long resolvedToday,
        long openTickets,
        long unassignedTickets,
        long criticalTickets,
        long slaAtRisk,
        long slaBreachedOpen
) {}