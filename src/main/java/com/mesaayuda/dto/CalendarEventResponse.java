package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record CalendarEventResponse(
        String id,
        String type,
        String title,
        OffsetDateTime start,
        OffsetDateTime end,
        String status,
        String color,
        UUID refId,
        /** Número visible del ticket; null en las tareas. */
        Long refNumber,
        boolean allDay,
        OffsetDateTime createdAt
) {}