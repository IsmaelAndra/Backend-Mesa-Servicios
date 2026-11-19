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
        @Size(max = 200) String enabledActions,
        /** Máximo de consultas por día (0 o vacío = sin límite). */
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100000) Integer dailyLimit,
        /** MAPI responde primero al crearse un ticket (V49). Null = no cambia. */
        Boolean respondePrimero
) {
    public AiConfigRequest(String provider, String model, String apiKey, String baseUrl, String providerIcon,
                           Boolean enabled, String rulesText, String enabledActions, Integer dailyLimit) {
        this(provider, model, apiKey, baseUrl, providerIcon, enabled, rulesText, enabledActions, dailyLimit, null);
    }

    /** Compatibilidad: guardado sin límite diario (se conserva el que había). */
    public AiConfigRequest(String provider, String model, String apiKey, String baseUrl, String providerIcon,
                           Boolean enabled, String rulesText, String enabledActions) {
        this(provider, model, apiKey, baseUrl, providerIcon, enabled, rulesText, enabledActions, null, null);
    }
}