package com.mesaayuda.dto;

/**
 * Politica de privacidad ya resuelta para el frontend.
 *
 * "edited" le dice al panel si el contenido es del super administrador o el que
 * trae el repositorio: si es false, la interfaz avisa de que una actualizacion
 * del codigo podria cambiarlo. No viaja "updatedBy" porque la pagina es publica.
 */
public record PrivacyConfigResponse(
        String privacyTitle,
        String privacyVersion,
        String privacyContent,
        String privacyContact,
        String privacyEffective,
        Boolean edited
) {
}