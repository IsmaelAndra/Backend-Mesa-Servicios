package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiConfigRequest(
        @NotBlank(message = "El proveedor es obligatorio") String provider,
        @NotBlank(message = "El modelo es obligatorio") String model,
        String apiKey,
        @Size(max = 500) String baseUrl,
        @Size(max = 500) String providerIcon,
        Boolean enabled,
        /**
         * Reglas y comportamiento esperado del asistente, escritas por la
         * institucion. Se anteponen a las instrucciones de cada accion.
         */
        @Size(max = 8000) String rulesText,
        /** Acciones habilitadas, separadas por comas: SUMMARY, SUGGEST, CHAT. */
        @Size(max = 200) String enabledActions
) {}