package com.mesaayuda.dto;
import java.util.UUID;

public record AiConfigResponse(
        UUID id,
        Boolean enabled,
        String provider,
        String model,
        /**
           * Si hay una clave configurada. No se devuelve la clave ni siquiera
           * enmascarada: con el sufijo de cuatro caracteres se puede identificar
           * una clave filtrada. Es el mismo criterio que usa la configuracion de
           * Microsoft Graph con su clientSecretConfigured.
           */
          Boolean apiKeyConfigured,
        String baseUrl,
        String providerIcon,
        String rulesText,
        String enabledActions
) {}