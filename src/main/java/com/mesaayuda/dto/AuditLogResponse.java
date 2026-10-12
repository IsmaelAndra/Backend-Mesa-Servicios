package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record AuditLogResponse(
        UUID idAudit,
        String actionType,
        String user,
        String oldValue,
        String newValue,
        OffsetDateTime createdAtAudit
) {}