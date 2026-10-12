package com.mesaayuda.dto;

/**
 * Textos de las pantallas de error, ya resueltos para el frontend.
 *
 * Este record es el que consume la vista publica, y no la entidad, por dos
 * motivos: no expone "updatedBy" ni las marcas de tiempo a cualquiera que entre
 * a la pagina de error, y obliga a que la conversion de nulos a cadenas vacias
 * ocurra una sola vez, en el servicio.
 */
public record ErrorPageConfigResponse(
        String error404Title,
        String error404Message,
        String error404Icon,
        String error404Color,
        String error403Title,
        String error403Message,
        String error403Icon,
        String error403Color,
        String error500Title,
        String error500Message,
        String error500Icon,
        String error500Color,
        String error503Title,
        String error503Message,
        String error503Icon,
        String error503Color,
        /** Comunes a todas las páginas de aviso. */
        String errorButtonText,
        boolean errorShowCode,
        boolean errorShowPrivacy
) {
}