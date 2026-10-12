package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.time.OffsetDateTime;

import java.util.UUID;
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

        String color
) {}