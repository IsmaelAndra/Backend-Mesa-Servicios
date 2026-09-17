package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record CalendarEventResponse(
        String id,
        String type,
        String title,
        OffsetDateTime start,
        OffsetDateTime end,
        String status,
        String color,
        Long refId,
        boolean allDay
) {}