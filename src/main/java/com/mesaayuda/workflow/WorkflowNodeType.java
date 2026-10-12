package com.mesaayuda.workflow;

/**
 * Tipos de nodo que el motor reconoce.
 *
 * <p><b>Lista cerrada, y esa es la garantía de seguridad.</b> No hay ninguna
 * forma de registrar un tipo nuevo desde fuera del código: el motor resuelve cada
 * tipo contra un {@code NodeHandler} que es un bean de Spring, y lo que no
 * resuelva se rechaza al guardar el workflow.
 *
 * <p>Esto es lo que impide que un workflow se convierta en una puerta trasera. Un
 * motor que aceptara un tipo de nodo "ejecuta esto" podría ejecutar lo que fuera.
 * Aquí la pregunta no es qué puede hacer un workflow, sino cuál de las acciones
 * que el sistema ha decidido permitir.
 *
 * <p>La lista empieza corta a propósito. Cada entrada nueva es una acción real
 * con su permiso, su auditoría y su prueba.
 */
public enum WorkflowNodeType {

    /** Arranque del grafo. Exactamente uno por workflow. */
    INICIO(false),

    /** Salida. Puede haber varios. */
    FIN(false),

    // ============================ LOGICA ============================
    /** Dos salidas: SI y NO. */
    IF(true),

    /** Una salida por valor. */
    SWITCH(true),

    /** Espera sin ocupar hilos. */
    WAIT(true),

    /** Espera a la decisión de una persona, respetando RBAC. */
    APPROVAL(true),

    // ============================ COMUNICACION ============================
    /** Notificación in-app y correo según la plantilla. */
    NOTIFY(true),

    /** Correo directo por Microsoft Graph. */
    EMAIL(true),

    // ============================ TICKETS ============================
    /**
     * Asignar agente o equipo.
     *
     * <p>Delega en TicketService.assign, que ya notifica, audita y emite SSE. El
     * nodo no vuelve a notificar: hacerlo significaría que el agente recibe dos
     * correos idénticos.
     */
    TICKET_ASSIGN(true),

    /** Cambiar estado. Delega en TicketService.changeStatus. */
    TICKET_STATUS(true),

    /** Agregar comentario. Delega en TicketService.addAnswer. */
    TICKET_COMMENT(true),

    /** Cambiar prioridad. */
    TICKET_PRIORITY(true),

    /** Cambiar categoría. */
    TICKET_CATEGORY(true),

    /** Cerrar. Azúcar sobre TICKET_STATUS con el estado fijo. */
    TICKET_CLOSE(true),

    /** Reabrir. Azúcar sobre TICKET_STATUS con el estado fijo. */
    TICKET_REOPEN(true),

    /** Pasar a un equipo y elegir agente dentro de él. Delega en TicketService.assignTeam. */
    TICKET_ASSIGN_TEAM(true),

    /** Crear un ticket nuevo. Delega en TicketService.create. */
    TICKET_CREATE(true),

    // ============================ OTROS ============================
    /** Crear tarea. Delega en TaskService.create. */
    TASK_CREATE(true),

    /** Aplicar SLA. Delega en SlaService.applySlaToTicket. */
    SLA_APPLY(true),

    /**
     * NEXA, la IA existente.
     *
     * <p>Solo genera texto: analiza, clasifica o resume. No modifica nada por su
     * cuenta; cualquier efecto pasa por un nodo de acción posterior.
     */
    NEXA(true),

    /** Registrar un evento en la auditoría del sistema. */
    AUDIT(true);

    private final boolean tieneConfiguracion;

    WorkflowNodeType(boolean tieneConfiguracion) {
        this.tieneConfiguracion = tieneConfiguracion;
    }

    /**
     * Si el nodo necesita configuración para hacer algo útil.
     *
     * <p>El validador lo usa para no exigir campos de un INICIO o un FIN, que solo
     * llevan etiqueta.
     */
    public boolean tieneConfiguracion() {
        return tieneConfiguracion;
    }

    /** ¿Este tipo tiene varias salidas? Lo decide el validador al exigir conectores. */
    public boolean esRama() {
        return this == IF || this == SWITCH;
    }

    /** Resuelve un nombre del JSON. Devuelve {@code null} si no existe. */
    public static WorkflowNodeType desde(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        try {
            return valueOf(nombre.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Puertos de salida de un tipo de nodo.
     *
     * <p>El motor los necesita para saber por dónde continuar. Es la información
     * que convierte al grafo en algo navegable y no en una lista.
     */
    public java.util.List<String> puertos() {
        if (this == IF) {
            return java.util.List.of("SI", "NO");
        }
        if (this == SWITCH) {
            return java.util.List.of();
        }
        if (this == FIN) {
            return java.util.List.of();
        }
        if (this == APPROVAL) {
            // VENCIDO solo se usa si el nodo tiene fecha límite. Las ramas que no
            // se conecten terminan la ejecución (nunca siguen como aprobadas).
            return java.util.List.of(com.mesaayuda.entity.WorkflowExecution.PUERTO_APROBADO,
                    com.mesaayuda.entity.WorkflowExecution.PUERTO_RECHAZADO,
                    com.mesaayuda.entity.WorkflowExecution.PUERTO_VENCIDO);
        }
        return java.util.List.of(WorkflowEdgeDef.PUERTO_UNICO);
    }
}