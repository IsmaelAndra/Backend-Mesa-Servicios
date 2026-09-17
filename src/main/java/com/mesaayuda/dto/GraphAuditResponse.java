package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record GraphAuditResponse(
        Long id,
        Long changedBy,
        String changedByEmail,
        String action,
        String details,
        String ipAddress,
        OffsetDateTime createdAt
) {}
