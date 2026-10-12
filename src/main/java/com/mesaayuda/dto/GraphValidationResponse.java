package com.mesaayuda.dto;

import java.util.List;

/**
 * Resultado de comprobar un grafo sin guardarlo.
 *
 * <p>Existe para que el constructor visual avise <b>antes</b> de publicar. Publicar
 * un grafo roto también falla, y con el mismo detalle, pero después de haber
 * escrito la versión en la base.
 */
public record GraphValidationResponse(
        boolean valid,
        List<String> errors
) {

    public GraphValidationResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}