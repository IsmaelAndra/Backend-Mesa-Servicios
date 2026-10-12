package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuickReplyRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 80, message = "El título admite como máximo 80 caracteres")
        String title,

        @NotBlank(message = "El texto es obligatorio")
        @Size(max = 5000, message = "El texto admite como máximo 5000 caracteres")
        String content,

        /** Compartida con todo el personal. Solo la puede marcar un administrador. */
        Boolean shared
) {}
