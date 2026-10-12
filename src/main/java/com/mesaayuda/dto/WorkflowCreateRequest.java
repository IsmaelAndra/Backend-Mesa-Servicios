package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta de un workflow.
 *
 * <p>Obsérvese que no hay ningún campo de permiso ni de rol. El ámbito con el que
 * se ejecutará el proceso lo decide el servidor a partir de quien guarda: si el
 * campo existiera, un usuario sin permisos podría escribirlo a mano.
 */
public record WorkflowCreateRequest(

        @NotBlank(message = "El nombre del workflow es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede pasar de 500 caracteres")
        String description,

        @NotBlank(message = "El disparador del workflow es obligatorio")
        String triggerType,

        @Size(max = 4000, message = "La configuración del disparador no puede pasar de 4000 caracteres")
        String triggerConfig
) {
}