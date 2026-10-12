package com.mesaayuda.dto;

import jakarta.validation.constraints.NotNull;

/** Payload de la configuración global de enrutamiento (SUPER_ADMIN). */
public record RoutingConfigRequest(
        @NotNull(message = "El estado de la asignación automática es obligatorio")
        Boolean autoAssignEnabled,
        Boolean notifyUnassigned
) {
}
