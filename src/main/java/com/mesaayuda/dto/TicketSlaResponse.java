package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record TicketSlaResponse(
        UUID idTicketSla,
        String slaPolicy,
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime responseCompletedAt,
        OffsetDateTime resolutionCompletedAt,
        boolean responseBreached,
        boolean resolutionBreached,
        int totalPausedMinutes
) {}