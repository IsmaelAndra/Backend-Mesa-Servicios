package com.mesaayuda.dto;

import java.util.List;

import java.util.UUID;
public record AuthResponse(
        String token,
        String tokenType,
        UUID userId,
        String email,
        String role,
        String area,
        List<String> permissions,
        Boolean mustChangePassword,
        String refreshToken,
        /** Segundos que le quedan al token de acceso, para el contador de sesion. */
        Long expiresInSeconds,
        /** Minutos de aviso configurados, para cuando el contador se ponga en rojo. */
        Integer warningMinutes,
        /** Si el frontend debe renovar el token solo. */
        Boolean autoRenew,
        /** Si el cierre por inactividad esta activo. */
        Boolean idleEnabled,
        /** Minutos sin actividad antes de cerrar la sesion. */
        Integer idleMinutes,

        /**
         * Códigos de recuperación, solo en el alta del doble factor.
         *
         * <p>Se devuelven una única vez, porque la tabla guarda únicamente el
         * hash. Si el usuario los pierde, tiene que regenerarlos.
         */
        List<String> recoveryCodes
) {
    /**
     * Copia sin el refresh token, que es lo que se devuelve al navegador.
     *
     * <p>El campo se conserva porque el servicio lo necesita para poner la cookie
     * HttpOnly, pero ya no debe viajar en el JSON: allí queda expuesto a cualquier
     * script de la página. La cookie es el único sitio donde debe vivir.
     */
    public AuthResponse sinRefreshToken() {
        return new AuthResponse(token, tokenType, userId, email, role, area, permissions,
                mustChangePassword, null, expiresInSeconds, warningMinutes, autoRenew,
                idleEnabled, idleMinutes, recoveryCodes);
    }
}