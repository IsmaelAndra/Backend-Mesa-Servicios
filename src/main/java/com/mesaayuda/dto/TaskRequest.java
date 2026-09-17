package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record TaskRequest(
        @NotBlank(message = "El título es obligatorio")
        String title,

        String description,

        String status,

        @NotNull(message = "El usuario asignado es obligatorio")
        Long assignedUserId,

        OffsetDateTime start,

        OffsetDateTime due,

        Integer durationMinutes,

        String color
) {}