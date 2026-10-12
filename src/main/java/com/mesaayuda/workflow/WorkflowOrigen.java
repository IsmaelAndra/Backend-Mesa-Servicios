package com.mesaayuda.workflow;

import java.util.UUID;
/**
 * Qué ejecución de workflow está actuando en este hilo, si alguna.
 *
 * <p>Es la pieza contra los bucles. Cuando un nodo cambia un ticket, TicketService
 * publica el evento como cualquier otro cambio; sin saber que lo provocó un
 * workflow, el evento creaba una ejecución nueva con profundidad 0, así que la
 * cadena A cambia el ticket → A se dispara otra vez → ... no tenía tope. El motor
 * deja aquí la ejecución en curso mientras corre un nodo, y el publicador de
 * eventos la lee en ese mismo instante (en el hilo del nodo, antes de que el
 * afterCommit lo pierda) para heredar la profundidad y saber quién lo originó.
 *
 * <p>ThreadLocal y no un campo: el worker ejecuta varias ejecuciones en paralelo
 * en hilos distintos. El motor la limpia siempre en un finally, porque el pool
 * reutiliza los hilos y un valor olvidado se atribuiría al trabajo siguiente.
 */
public final class WorkflowOrigen {

    /** Ejecución en curso, su workflow y su profundidad en la cascada. */
    public record Datos(UUID ejecucionId, UUID workflowId, int profundidad) {
    }

    private static final ThreadLocal<Datos> ACTUAL = new ThreadLocal<>();

    private WorkflowOrigen() {
    }

    public static void establecer(UUID ejecucionId, UUID workflowId, int profundidad) {
        ACTUAL.set(new Datos(ejecucionId, workflowId, profundidad));
    }

    /** La ejecución que está actuando en este hilo, o null si el cambio no viene de un workflow. */
    public static Datos actual() {
        return ACTUAL.get();
    }

    public static void limpiar() {
        ACTUAL.remove();
    }
}
