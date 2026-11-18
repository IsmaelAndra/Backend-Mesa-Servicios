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
         * Sucursal a la que corresponde el caso. No tiene por que ser la del
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
         * defecto). Así MAPI no la cambia después.
         */
        Boolean priorityManual,

        /**
         * Área que atiende el ticket (V28). Si viene categoría, manda la de la
         * categoría; sin categoría se toma la primera de esta área.
         */
        UUID areaDestinoId,

        /** Confidencial desde el alta (p. ej. un tema personal con Talento Humano). */
        Boolean confidencial,

        /**
         * Agente al que el jefe del área que atiende (o el súper admin) asigna el ticket
         * al crearlo. Vacío = reparto automático del equipo.
         */
        UUID agentId
) {
    /** Compatibilidad: sin agente elegido (reparto automático). */
    public TicketRequest(String title, String description, UUID categoryId, UUID priorityId, UUID typeId,
                         UUID sucursalId, UUID requesterId, Boolean priorityManual, UUID areaDestinoId,
                         Boolean confidencial) {
        this(title, description, categoryId, priorityId, typeId, sucursalId, requesterId, priorityManual,
                areaDestinoId, confidencial, null);
    }

    /** Compatibilidad: ticket a nombre de quien lo crea. */
    public TicketRequest(String title, String description, UUID categoryId, UUID priorityId,
                         UUID typeId, UUID sucursalId) {
        this(title, description, categoryId, priorityId, typeId, sucursalId, null, null, null, null);
    }

    /** Compatibilidad: sin área (la decide la categoría). */
    public TicketRequest(String title, String description, UUID categoryId, UUID priorityId,
                         UUID typeId, UUID sucursalId, UUID requesterId, Boolean priorityManual) {
        this(title, description, categoryId, priorityId, typeId, sucursalId, requesterId, priorityManual, null, null);
    }

    /** Compatibilidad: sin confidencialidad. */
    public TicketRequest(String title, String description, UUID categoryId, UUID priorityId,
                         UUID typeId, UUID sucursalId, UUID requesterId, Boolean priorityManual, UUID areaDestinoId) {
        this(title, description, categoryId, priorityId, typeId, sucursalId, requesterId, priorityManual,
                areaDestinoId, null);
    }
}