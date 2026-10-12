package com.mesaayuda.dto;

public record AiStatusResponse(
        Boolean enabled,
        String provider,
        String model,
        String providerIcon,
        /** Acciones habilitadas por el administrador: SUMMARY, SUGGEST, CHAT. */
        String enabledActions
) {}