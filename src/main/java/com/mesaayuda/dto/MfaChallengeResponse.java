package com.mesaayuda.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Respuesta del login cuando el doble factor está activo.
 *
 * <p>En este caso NO vienen tokens: vienen el desafío y, si el usuario todavía no
 * está enrolado, los datos para enrolarse. Los tokens solo salen al confirmar el
 * código en /auth/mfa/confirm.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MfaChallengeResponse(
        /** Discrimina en el frontend: hay que confirmar un código o hay que enrolarse. */
        String status,

        /** Token del desafío, de un solo uso. */
        String challenge,

        /** Solo en alta: URI otpauth que hay que convertir en QR. */
        String qrUri,

        /** Solo en alta: la clave para teclear a mano si el QR no funciona. */
        String claveManual,

        /** Nombre que verá en su app, para que no dude de si el QR es el correcto. */
        String emisor,

        /**
         * Códigos de recuperación, SOLO cuando acaba de enrolarse.
         *
         * <p>Se devuelven una vez y no se vuelven a guardar en claro, así que
         * esta es la única oportunidad que tiene el usuario de anotarlos. Si los
         * pierde, tiene que regenerarlos.
         */
        java.util.List<String> codigosRecuperacion
) {
    public static final String STATUS_VERIFICAR = "VERIFICAR";
    public static final String STATUS_ENROLAR = "ENROLAR";
}
