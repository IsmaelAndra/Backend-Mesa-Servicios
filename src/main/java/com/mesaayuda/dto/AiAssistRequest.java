package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
public record AiAssistRequest(
        @NotBlank(message = "La acción es obligatoria") String action,
        @NotNull(message = "El ticket es obligatorio") UUID ticketId,
        String instructions
) {}