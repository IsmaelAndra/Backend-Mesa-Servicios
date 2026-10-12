package com.mesaayuda.exception;

/**
 * El correo no pudo entregarse. Se usa solo en el camino síncrono (el
 * planificador de reintentos), que necesita distinguir "se envió" de "no se
 * envió" para no marcar como enviada una notificación que nunca salió.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
