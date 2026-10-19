package com.mesaayuda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String newPassword,

        /** Código de la app de autenticación, si la persona tiene el doble factor. */
        String codigoMfa
) {
    /** Compatibilidad: cambio sin código (cuentas sin doble factor). */
    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this(currentPassword, newPassword, null);
    }
}