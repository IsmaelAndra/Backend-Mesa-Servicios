package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record MicrosoftGraphConfigRequest(
        String tenantId,
        String clientId,
        /** Si viene vacío o con asteriscos, se conserva el secreto existente. */
        String clientSecret,
        String scope,
        String grantType,
        OffsetDateTime secretExpiresAt,
        Boolean enabled,
        Integer syncIntervalMinutes
) {}
