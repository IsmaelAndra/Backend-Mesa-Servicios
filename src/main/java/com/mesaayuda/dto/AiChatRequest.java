package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición de chat con el asistente IA.
 *
 * @param context contexto opcional que se inyecta al prompt (p. ej. descripción
 *                y hilo completo del ticket, o instrucciones para crear un ticket nuevo)
 * @param session identificador de la conversación (se usa por usuario si viene vacío)
 */
public record AiChatRequest(
        @NotBlank(message = "El mensaje es obligatorio") String message,
        String context,
        String session
) {}