package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record AuditEventResponse(
        Long idAudit,
        String username,
        String action,
        String entity,
        Long entityId,
        String ipAddress,
        OffsetDateTime createdAt
) {}