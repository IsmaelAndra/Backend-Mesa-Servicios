package com.mesaayuda.workflow;

import java.util.List;
import java.util.Map;

/**
 * El grafo completo de un workflow, tal y como viaja en JSONB.
 *
 * <p>Guarda además la posición de cada nodo. No es un adorno del constructor
 * visual: es lo que permite reabrir un workflow en el mismo sitio donde se dejó,
 * en vez de en un amasijo de cajas superpuestas en el origen.
 *
 * @param name      nombre del grafo. Se duplica aquí para poder leerlo sin abrir
 *                  la versión, que es lo que hace el listado
 * @param nodes     nodos
 * @param edges     conectores
 * @param variables valores iniciales opcionales, para procesos que empiezan con
 *                  constantes
 */
public record WorkflowGraphDef(
        String name,
        List<WorkflowNodeDef> nodes,
        List<WorkflowEdgeDef> edges,
        Map<String, Object> variables
) {

    /** Nodo de arranque. El grafo tiene exactamente uno. */
    public static final String TIPO_INICIO = "INICIO";
    /** Nodo de salida. Puede haber varios. */
    public static final String TIPO_FIN = "FIN";

    public WorkflowGraphDef {
        if (nodes == null) {
            nodes = List.of();
        }
        if (edges == null) {
            edges = List.of();
        }
        if (variables == null) {
            variables = Map.of();
        }
    }

    public List<WorkflowNodeDef> nodos() {
        return nodes == null ? List.of() : nodes;
    }

    public List<WorkflowEdgeDef> conectores() {
        return edges == null ? List.of() : edges;
    }

    public Map<String, Object> variablesIniciales() {
        return variables == null ? Map.of() : variables;
    }

    /** Nodo de arranque, o {@code null} si el grafo no lo tiene. */
    public WorkflowNodeDef nodoInicio() {
        return nodos().stream()
                .filter(n -> TIPO_INICIO.equals(n.type()))
                .findFirst()
                .orElse(null);
    }

    public List<WorkflowNodeDef> nodosFin() {
        return nodos().stream().filter(n -> TIPO_FIN.equals(n.type())).toList();
    }

    /** Conectores que salen de un nodo por un puerto concreto. */
    public List<WorkflowEdgeDef> salidasDe(String nodeId, String puerto) {
        return conectores().stream()
                .filter(e -> nodeId.equals(e.from()) && puerto.equals(e.puerto()))
                .toList();
    }
}