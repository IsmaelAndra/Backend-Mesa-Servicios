package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Texto de las pantallas de error, tal y como lo edita el panel.
 *
 * Todos los campos son obligatorios: la pantalla de error siempre necesita un
 * titulo y un mensaje que mostrar. El color y el icono tambien, con el respaldo
 * ya puesto en el servicio si llegan vacios o con formato invalido.
 */
public record ErrorPageConfigRequest(

        @NotBlank(message = "El titulo del 404 es obligatorio")
        @Size(max = 160, message = "El titulo del 404 no puede exceder 160 caracteres")
        String error404Title,

        @NotBlank(message = "El mensaje del 404 es obligatorio")
        @Size(max = 1000, message = "El mensaje del 404 no puede exceder 1000 caracteres")
        String error404Message,

        @Size(max = 120, message = "El icono del 404 no puede exceder 120 caracteres")
        String error404Icon,

        @Size(max = 7, message = "El color del 404 debe ser un hexadecimal de 7 caracteres")
        @Pattern(regexp = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "El color del 404 debe ser un hexadecimal, por ejemplo #64748b")
        String error404Color,

        @NotBlank(message = "El titulo del 403 es obligatorio")
        @Size(max = 160, message = "El titulo del 403 no puede exceder 160 caracteres")
        String error403Title,

        @NotBlank(message = "El mensaje del 403 es obligatorio")
        @Size(max = 1000, message = "El mensaje del 403 no puede exceder 1000 caracteres")
        String error403Message,

        @Size(max = 120, message = "El icono del 403 no puede exceder 120 caracteres")
        String error403Icon,

        @Size(max = 7, message = "El color del 403 debe ser un hexadecimal de 7 caracteres")
        @Pattern(regexp = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "El color del 403 debe ser un hexadecimal, por ejemplo #d97706")
        String error403Color,

        @NotBlank(message = "El titulo del 500 es obligatorio")
        @Size(max = 160, message = "El titulo del 500 no puede exceder 160 caracteres")
        String error500Title,

        @NotBlank(message = "El mensaje del 500 es obligatorio")
        @Size(max = 1000, message = "El mensaje del 500 no puede exceder 1000 caracteres")
        String error500Message,

        @Size(max = 120, message = "El icono del 500 no puede exceder 120 caracteres")
        String error500Icon,

        @Size(max = 7, message = "El color del 500 debe ser un hexadecimal de 7 caracteres")
        @Pattern(regexp = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "El color del 500 debe ser un hexadecimal, por ejemplo #dc2626")
        String error500Color,

        @NotBlank(message = "El titulo del 503 es obligatorio")
        @Size(max = 160, message = "El titulo del 503 no puede exceder 160 caracteres")
        String error503Title,

        @NotBlank(message = "El mensaje del 503 es obligatorio")
        @Size(max = 1000, message = "El mensaje del 503 no puede exceder 1000 caracteres")
        String error503Message,

        @Size(max = 120, message = "El icono del 503 no puede exceder 120 caracteres")
        String error503Icon,

        @Size(max = 7, message = "El color del 503 debe ser un hexadecimal de 7 caracteres")
        @Pattern(regexp = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "El color del 503 debe ser un hexadecimal, por ejemplo #2563eb")
        String error503Color,

        /** Texto del botón principal; vacío o null conserva el actual. */
        @Size(max = 60, message = "El texto del botón no puede exceder 60 caracteres")
        String errorButtonText,

        /** Mostrar el código grande ("404"); null conserva el actual. */
        Boolean errorShowCode,

        /** Mostrar el enlace a la política de privacidad; null conserva el actual. */
        Boolean errorShowPrivacy
) {
    /** Compatibilidad con quien manda solo los cuatro estados (sin los ajustes comunes). */
    public ErrorPageConfigRequest(String error404Title, String error404Message, String error404Icon, String error404Color,
                                  String error403Title, String error403Message, String error403Icon, String error403Color,
                                  String error500Title, String error500Message, String error500Icon, String error500Color,
                                  String error503Title, String error503Message, String error503Icon, String error503Color) {
        this(error404Title, error404Message, error404Icon, error404Color,
                error403Title, error403Message, error403Icon, error403Color,
                error500Title, error500Message, error500Icon, error500Color,
                error503Title, error503Message, error503Icon, error503Color,
                null, null, null);
    }
}