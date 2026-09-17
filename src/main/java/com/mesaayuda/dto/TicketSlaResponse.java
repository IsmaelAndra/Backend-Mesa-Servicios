package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record TicketSlaResponse(
        Long idTicketSla,
        String slaPolicy,
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime responseCompletedAt,
        OffsetDateTime resolutionCompletedAt,
        boolean responseBreached,
        boolean resolutionBreached,
        int totalPausedMinutes
) {}