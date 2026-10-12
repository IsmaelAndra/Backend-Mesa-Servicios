package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Respuesta de error estandar de la API.
 * <p>
 * {@code requestId} es la pieza que hace util el log del servidor: el cliente
 * puede copiarlo y correlacionarlo con la linea exacta del log, sin que eso
 * obligue a devolverle el detalle interno de la excepcion.
 */
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details,
        String requestId
) {}
