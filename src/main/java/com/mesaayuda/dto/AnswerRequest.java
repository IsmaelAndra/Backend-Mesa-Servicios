package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerRequest(
        @NotBlank(message = "El mensaje es obligatorio")
        String message,

        Boolean internal
) {}