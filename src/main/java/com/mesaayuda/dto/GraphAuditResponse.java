package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record GraphAuditResponse(
        UUID id,
        UUID changedBy,
        String changedByEmail,
        String action,
        String details,
        String ipAddress,
        OffsetDateTime createdAt
) {}
