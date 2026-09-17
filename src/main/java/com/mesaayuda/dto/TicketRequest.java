package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede exceder 255 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        String description,

        Long categoryId,

        Long priorityId,

        Long typeId
) {}