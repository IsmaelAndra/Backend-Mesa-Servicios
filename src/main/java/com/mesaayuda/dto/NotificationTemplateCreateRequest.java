package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creación de una plantilla de notificación (con destinatario de correo). */
public record NotificationTemplateCreateRequest(
        @NotBlank @Size(max = 100) String nameTemplate,
        @NotBlank @Size(max = 50) String typeTemplate,
        @NotBlank @Size(max = 255) String subjectTemplate,
        @NotBlank String bodyTemplate,
        Boolean activeTemplate,
        @Size(max = 50) String recipientRule,
        @Size(max = 255) String recipientEmail,
        @Size(max = 100) String iconTemplate,
        @Size(max = 30) String colorTemplate,
        @Size(max = 500) String imageUrl
) {}