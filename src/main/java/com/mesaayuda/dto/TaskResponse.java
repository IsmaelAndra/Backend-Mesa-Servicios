package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record TaskResponse(
        UUID id,
        String title,
        String description,
        String status,
        UUID assignedUserId,
        String assignedUserEmail,
        UUID createdByUserId,
        String createdByEmail,
        OffsetDateTime start,
        OffsetDateTime due,
        Integer durationMinutes,
        String color,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}