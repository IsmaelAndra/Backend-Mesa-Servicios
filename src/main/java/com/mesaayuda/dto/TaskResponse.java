package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        String status,
        Long assignedUserId,
        String assignedUserEmail,
        Long createdByUserId,
        String createdByEmail,
        OffsetDateTime start,
        OffsetDateTime due,
        Integer durationMinutes,
        String color,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}