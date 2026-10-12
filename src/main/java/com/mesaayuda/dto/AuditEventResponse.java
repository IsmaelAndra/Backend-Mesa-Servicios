package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record AuditEventResponse(
        UUID idAudit,
        String username,
        String action,
        String entity,
        UUID entityId,
        String ipAddress,
        OffsetDateTime createdAt
) {}