package com.mesaayuda.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Ajuste del doble factor por parte del super administrador.
 *
 * <p>Se separa del resto de la configuración porque cambiar esto tiene
 * consecuencias sobre cuentas ajenas: al activarlo, todos los usuarios que no
 * tengan app pasan a necesitarla.
 */
public record MfaSettingRequest(

        @NotNull(message = "Debe indicar si el doble factor queda activo")
        Boolean mfaEnabled,

        String issuer
) {
}
