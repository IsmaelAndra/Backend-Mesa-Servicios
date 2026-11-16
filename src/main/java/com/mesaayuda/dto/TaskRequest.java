package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * recordatorioMinutos: cuántos minutos antes del plazo se avisa (null = sin
 * aviso). Solo tiene efecto si la actividad tiene fecha límite.
 */
public record TaskRequest(
        @NotBlank(message = "El título es obligatorio")
        String title,

        String description,

        String status,

        @NotNull(message = "El usuario asignado es obligatorio")
        UUID assignedUserId,

        OffsetDateTime start,

        OffsetDateTime due,

        Integer durationMinutes,

        String color,

        Integer recordatorioMinutos
) {
    public TaskRequest(String title, String description, String status, UUID assignedUserId,
                       OffsetDateTime start, OffsetDateTime due, Integer durationMinutes, String color) {
        this(title, description, status, assignedUserId, start, due, durationMinutes, color, null);
    }
}
