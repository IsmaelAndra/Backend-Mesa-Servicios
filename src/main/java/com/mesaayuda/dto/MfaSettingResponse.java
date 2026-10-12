package com.mesaayuda.dto;

/**
 * Estado del doble factor, para la pantalla de ajuste.
 *
 * <p>{@code enrolledCount} está para que el super administrador vea a quién les
 * va a afectar el cambio antes de activarlo: si hay 40 personas sin enrolar, no
 * es lo mismo que si solo falta una.
 */
public record MfaSettingResponse(
        boolean mfaEnabled,
        String issuer,
        long enrolledCount,
        /** Si el propio super administrador tiene el doble factor puesto. */
        boolean superAdminEnrolled
) {
}
