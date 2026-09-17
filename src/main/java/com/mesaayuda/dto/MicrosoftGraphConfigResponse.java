package com.mesaayuda.dto;

import java.time.OffsetDateTime;

/**
 * Respuesta segura: NUNCA incluye el client_secret almacenado,
 * solo si está configurado.
 */
public record MicrosoftGraphConfigResponse(
        Long id,
        String tenantId,
        String clientId,
        boolean clientSecretConfigured,
        String scope,
        String grantType,
        OffsetDateTime secretExpiresAt,
        boolean enabled,
        Integer syncIntervalMinutes,
        OffsetDateTime lastSyncAt,
        OffsetDateTime updatedAt,
        /** CONNECTED | INCOMPLETE | DISABLED */
        String status
) {}
