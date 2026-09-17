package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;

public record AiConfigRequest(
        @NotBlank(message = "El proveedor es obligatorio") String provider,
        @NotBlank(message = "El modelo es obligatorio") String model,
        String apiKey,
        String baseUrl,
        Boolean enabled
) {}