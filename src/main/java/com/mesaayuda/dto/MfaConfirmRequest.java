package com.mesaayuda.dto;

/**
 * Confirmación del segundo factor.
 *
 * <p>El mismo request sirve para las dos fases a propósito: tanto enrolarse como
 * verificar un login consisten en mandar un desafío y un código de seis dígitos.
 * Separarlos en dos endpoints solo añadiría superficie sin ganar nada.
 */
public record MfaConfirmRequest(

        String challenge,

        /** Código de seis dígitos de la app. */
        String codigo,

        /**
         * Código de recuperación, por si la app no está a mano.
         *
         * <p>No puede venir a la vez que el código normal: si se aceptaran los
         * dos, un código de recuperación quemado en el mismo intento que un código
         * válido serviría para algo.
         */
        String codigoRecuperacion
) {
}
