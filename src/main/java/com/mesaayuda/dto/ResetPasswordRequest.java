package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmación del enlace de restablecimiento.
 *
 * <p>Las reglas de longitud son las mismas que en {@link ChangePasswordRequest}.
 * Duplicarlas aquí a propósito: el endpoint es público (lo usa quien todavía no
 * puede iniciar sesión) y no quiero que un cambio futuro en el DTO del cambio de
 * contraseña abra esta puerta por descuido.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "El enlace de restablecimiento es obligatorio")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String nuevaPassword
) {
}