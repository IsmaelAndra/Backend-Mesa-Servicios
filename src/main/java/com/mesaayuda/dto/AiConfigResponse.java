package com.mesaayuda.dto;

public record AiConfigResponse(
        Long id,
        Boolean enabled,
        String provider,
        String model,
        String apiKeyMasked,
        String baseUrl
) {}