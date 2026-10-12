package com.mesaayuda.dto;

import jakarta.validation.constraints.Size;

/**
 * Actualización parcial de una plantilla de notificación, incluida la
 * configuración del destinatario de correo (recipient_rule / recipient_email).
 */
public record NotificationTemplateUpdateRequest(
        @Size(max = 255) String subjectTemplate,
        String bodyTemplate,
        Boolean activeTemplate,
        /** DEFAULT | SOLICITANTE | AGENTE | AMBOS | CUSTOM */
        @Size(max = 50) String recipientRule,
        @Size(max = 255) String recipientEmail,
        @Size(max = 100) String iconTemplate,
        @Size(max = 30) String colorTemplate,
        /** Imagen propia de la plantilla, usada por [imagen] dentro del HTML. */
        @Size(max = 500) String imageUrl
) {}