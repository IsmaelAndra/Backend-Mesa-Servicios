package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Trasladar un ticket a otra área. La categoría es opcional (si no se indica se
 * usa la primera del área); el motivo es obligatorio porque queda en el historial
 * y lo lee el área que lo recibe.
 */
public record TrasladoRequest(
        @NotNull(message = "Indique el área a la que se traslada")
        UUID areaId,
        UUID categoryId,
        @NotBlank(message = "Indique el motivo del traslado")
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String motivo
) {
}
