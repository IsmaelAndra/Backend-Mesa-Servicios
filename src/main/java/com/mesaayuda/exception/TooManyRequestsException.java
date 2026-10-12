package com.mesaayuda.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Se ha superado el límite de peticiones de una acción. Se responde 429, no 400:
 * la petición es válida, simplemente ahora no se permite continuar.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
