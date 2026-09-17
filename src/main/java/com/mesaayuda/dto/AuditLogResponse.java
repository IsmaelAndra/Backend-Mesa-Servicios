package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long idAudit,
        String actionType,
        String user,
        String oldValue,
        String newValue,
        OffsetDateTime createdAtAudit
) {}