package com.mesaayuda.dto;

import java.time.OffsetDateTime;

/**
 * Configuración de un sonido, sin el audio (se descarga aparte con
 * {@code GET /sounds/{scope}/{key}/audio}; {@code updatedAt} sirve de versión).
 */
public record NotificationSoundResponse(
        String scope,
        String key,
        String urgency,
        boolean hasAudio,
        String fileName,
        OffsetDateTime updatedAt) {
}
