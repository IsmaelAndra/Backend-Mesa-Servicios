package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusChangeRequest(
        @NotBlank(message = "El nuevo estado es obligatorio")
        String statusName
) {}