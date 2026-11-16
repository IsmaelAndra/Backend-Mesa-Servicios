package com.mesaayuda.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * 403 de un ticket confidencial. Lleva su propio mensaje para que la pantalla
 * ofrezca "Solicitar acceso" en vez de un acceso denegado sin salida.
 */
public class TicketConfidencialException extends AccessDeniedException {

    public static final String MENSAJE = "Ticket confidencial: solicite acceso al jefe del área que lo atiende";

    public TicketConfidencialException() {
        super(MENSAJE);
    }
}
