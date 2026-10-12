package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Un correo de la cola, tal como lo ve el panel. Nunca lleva el cuerpo: puede contener enlaces o claves. */
public record MailOutboxItemResponse(
        UUID id,
        String to,
        String subject,
        String type,
        int priority,
        String status,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime sentAt
) {}
