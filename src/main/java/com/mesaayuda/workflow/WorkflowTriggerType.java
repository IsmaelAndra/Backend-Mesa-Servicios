package com.mesaayuda.workflow;

import java.util.Locale;

/**
 * Disparadores que conoce el motor. Lista cerrada, como los tipos de nodo.
 *
 * <p>Antes el disparador era texto libre: se podía guardar un workflow con
 * "TICKET_CREADO" en lugar de "TICKET_CREATED" y no saltaba ningún error; el
 * workflow quedaba activo y no se disparaba nunca. Ahora lo que no está aquí se
 * rechaza al guardar.
 *
 * <p>Para añadir un disparador: un valor aquí y la llamada al publicador en el
 * servicio donde ocurre el hecho. Nada más.
 */
public enum WorkflowTriggerType {

    TICKET_CREATED("Ticket creado", Entidad.TICKET),
    TICKET_UPDATED("Ticket actualizado", Entidad.TICKET),
    TICKET_ASSIGNED("Ticket asignado", Entidad.TICKET),
    TICKET_STATUS_CHANGED("Ticket cambió de estado", Entidad.TICKET),
    TICKET_CLOSED("Ticket cerrado", Entidad.TICKET),
    TICKET_REOPENED("Ticket reabierto", Entidad.TICKET),
    TICKET_PRIORITY_CHANGED("Cambio de prioridad", Entidad.TICKET),
    TICKET_CATEGORY_CHANGED("Cambio de categoría", Entidad.TICKET),

    SLA_WARNING("SLA cercano al límite", Entidad.TICKET),
    SLA_BREACHED("SLA incumplido", Entidad.TICKET),

    USER_CREATED("Usuario creado", Entidad.USUARIO),
    USER_UPDATED("Usuario actualizado", Entidad.USUARIO),
    USER_DEACTIVATED("Usuario desactivado", Entidad.USUARIO),

    /** Cada N minutos, con una expresión cron o una sola vez en una fecha. */
    SCHEDULED("Programado", Entidad.NINGUNA),

    /** Solo se lanza a mano, desde la interfaz. */
    MANUAL("Manual", Entidad.NINGUNA);

    /** A qué se refieren las variables vivas de una ejecución con este disparador. */
    public enum Entidad { TICKET, USUARIO, NINGUNA }

    private final String descripcion;
    private final Entidad entidad;

    WorkflowTriggerType(String descripcion, Entidad entidad) {
        this.descripcion = descripcion;
        this.entidad = entidad;
    }

    public String descripcion() {
        return descripcion;
    }

    public Entidad entidad() {
        return entidad;
    }

    /** El disparador con ese nombre, o null si no existe. */
    public static WorkflowTriggerType desde(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        try {
            return valueOf(nombre.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
