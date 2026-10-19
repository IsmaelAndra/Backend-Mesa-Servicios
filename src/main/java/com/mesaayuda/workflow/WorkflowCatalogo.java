package com.mesaayuda.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que el constructor visual necesita saber del motor: disparadores, tipos de
 * nodo con sus puertos, variables disponibles y lo que deja cada nodo.
 *
 * <p>Sale del servidor para que el constructor no ofrezca nada que el motor no
 * sepa ejecutar. Las etiquetas y formularios de cada nodo sí viven en el
 * frontend: son presentación.
 */
public final class WorkflowCatalogo {

    private WorkflowCatalogo() {
    }

    /** Campos que deja el evento en {@code evento.*}, según la familia del disparador. */
    private static final Map<String, String> EVENTO_TICKET = orden(
            "evento.ticketId", "Id del ticket",
            "evento.titulo", "Título en el momento del evento",
            "evento.estado", "Estado en el momento del evento",
            "evento.prioridad", "Prioridad en el momento del evento",
            "evento.categoria", "Categoría en el momento del evento",
            "evento.area", "Área",
            "evento.solicitanteId", "Id del solicitante",
            "evento.solicitanteCorreo", "Correo del solicitante",
            "evento.agenteId", "Id del agente en el momento del evento");

    private static final Map<String, Map<String, String>> EVENTO_EXTRA = Map.of(
            "TICKET_STATUS_CHANGED", orden("evento.estadoAnterior", "Estado anterior"),
            "TICKET_CLOSED", orden("evento.estadoAnterior", "Estado anterior"),
            "TICKET_REOPENED", orden("evento.estadoAnterior", "Estado anterior"),
            "TICKET_PRIORITY_CHANGED", orden("evento.prioridadAnterior", "Prioridad anterior"),
            "TICKET_CATEGORY_CHANGED", orden("evento.categoriaAnterior", "Categoría anterior"),
            "SLA_WARNING", orden("evento.slaPorcentaje", "Porcentaje del plazo consumido",
                    "evento.slaVence", "Vencimiento de la respuesta"),
            "SLA_BREACHED", orden("evento.slaVence", "Vencimiento de la respuesta"),
            "USER_DEACTIVATED", orden("evento.estadoAnterior", "Estado anterior"));

    private static final Map<String, String> EVENTO_USUARIO = orden(
            "evento.usuarioId", "Id del usuario",
            "evento.correo", "Correo",
            "evento.rol", "Rol",
            "evento.estado", "Estado");

    /** Lo que cada tipo de nodo deja en {@code nodos.<id>.*}. */
    public static final Map<String, Map<String, String>> SALIDAS_NODO = Map.ofEntries(
            Map.entry("NEXA", orden(
                    "prioridad", "Prioridad sugerida (CLASIFICAR)",
                    "prioridadId", "Id de la prioridad sugerida",
                    "categoria", "Categoría sugerida",
                    "categoriaId", "Id de la categoría sugerida",
                    "tipo", "Tipo de ticket sugerido",
                    "tipoId", "Id del tipo sugerido",
                    "equipo", "Equipo sugerido",
                    "equipoId", "Id del equipo sugerido",
                    "resumen", "Resumen corto",
                    "confianza", "Confianza de 0 a 1",
                    "texto", "Texto (RESUMIR / RESPONDER)")),
            Map.entry("APPROVAL", orden(
                    "decision", "APROBADO, RECHAZADO o VENCIDO",
                    "comentario", "Comentario de quien decidió",
                    "decididoPor", "Correo de quien decidió",
                    "solicitud", "Texto de la solicitud")),
            Map.entry("TICKET_CREATE", orden("ticketId", "Id interno del ticket creado",
                    "ticketNumero", "Número del ticket creado (#43)")),
            Map.entry("TICKET_ASSIGN", orden("agenteId", "Agente asignado", "equipoId", "Equipo")),
            Map.entry("TICKET_ASSIGN_TEAM", orden("equipoId", "Equipo", "agente", "Agente elegido")),
            Map.entry("TICKET_COMMENT", orden("respuestaId", "Id del comentario")),
            Map.entry("TASK_CREATE", orden("tareaId", "Id de la tarea")),
            Map.entry("SLA_APPLY", orden("politica", "Política aplicada", "venceResolucion", "Vencimiento")),
            Map.entry("NOTIFY", orden("avisados", "Personas avisadas")),
            Map.entry("EMAIL", orden("para", "Destinatario")),
            Map.entry("WAIT", orden("reanudaEn", "Cuándo se reanuda")));

    public static final List<String> OPERADORES = List.of("==", "!=", ">", "<", ">=", "<=",
            "contiene", "empieza_con", "termina_con", "vacio", "no_vacio", "es_verdadero", "es_falso");

    /** El catálogo completo, listo para serializar. */
    public static Map<String, Object> completo() {
        List<Map<String, Object>> disparadores = new ArrayList<>();
        for (WorkflowTriggerType t : WorkflowTriggerType.values()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("tipo", t.name());
            d.put("descripcion", t.descripcion());
            d.put("entidad", t.entidad().name());
            d.put("variables", variablesDe(t));
            disparadores.add(d);
        }
        List<Map<String, Object>> nodos = new ArrayList<>();
        for (WorkflowNodeType n : WorkflowNodeType.values()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("tipo", n.name());
            d.put("puertos", n.puertos());
            d.put("salidas", SALIDAS_NODO.getOrDefault(n.name(), Map.of()));
            nodos.add(d);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("disparadores", disparadores);
        r.put("nodos", nodos);
        r.put("operadores", OPERADORES);
        return r;
    }

    /** Variables que ofrece el selector para un disparador. */
    static Map<String, String> variablesDe(WorkflowTriggerType t) {
        Map<String, String> v = new LinkedHashMap<>();
        switch (t.entidad()) {
            case TICKET -> {
                v.putAll(WorkflowVariables.CATALOGO_TICKET);
                v.putAll(EVENTO_TICKET);
            }
            case USUARIO -> {
                v.putAll(WorkflowVariables.CATALOGO_USUARIO);
                v.putAll(EVENTO_USUARIO);
            }
            default -> {
                if (t == WorkflowTriggerType.MANUAL) {
                    // A mano se puede elegir un ticket o un usuario.
                    v.putAll(WorkflowVariables.CATALOGO_TICKET);
                    v.putAll(WorkflowVariables.CATALOGO_USUARIO);
                } else {
                    v.put("evento.programadoPara", "Instante programado de la ejecución");
                }
            }
        }
        v.putAll(EVENTO_EXTRA.getOrDefault(t.name(), Map.of()));
        v.put("ejecucion.id", "Id de la ejecución");
        v.put("ejecucion.version", "Versión del workflow");
        v.put("ejecucion.workflow", "Nombre del workflow");
        return v;
    }

    private static Map<String, String> orden(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }
}
