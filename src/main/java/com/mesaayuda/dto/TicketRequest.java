package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;
public record TicketRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede exceder 255 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        String description,

        UUID categoryId,

        UUID priorityId,

        UUID typeId,

        /**
         * Sede a la que corresponde el caso. No tiene por que ser la del
         * solicitante: alguien de Quito puede abrir un ticket que hay que
         * resolver en Guayaquil.
         */
        UUID sucursalId,

        /**
         * Solicitante, cuando el personal abre el ticket a nombre de otra persona
         * (por ejemplo desde la búsqueda global). Vacío = quien lo crea.
         */
        UUID requesterId,

        /**
         * true si la persona eligió la prioridad a mano (no dejó la de por
         * defecto). Así NEXA no la cambia después.
         */
        Boolean priorityManual
) {
    /** Compatibilidad: ticket a nombre de quien lo crea. */
    public TicketRequest(String title, String description, UUID categoryId, UUID priorityId,
                         UUID typeId, UUID sucursalId) {
        this(title, description, categoryId, priorityId, typeId, sucursalId, null, null);
    }
}