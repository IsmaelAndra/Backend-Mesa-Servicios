package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Respuesta segura: NUNCA incluye el client_secret almacenado,
 * solo si está configurado.
 */
public record MicrosoftGraphConfigResponse(
        UUID id,
        String tenantId,
        String clientId,
        boolean clientSecretConfigured,
        String scope,
        String grantType,
        OffsetDateTime secretExpiresAt,
        boolean enabled,
        Integer syncIntervalMinutes,
        boolean mailEnabled,
        String mailFromAccount,
        OffsetDateTime lastSyncAt,
        OffsetDateTime updatedAt,
        /** CONNECTED | INCOMPLETE | DISABLED */
        String status
) {}
