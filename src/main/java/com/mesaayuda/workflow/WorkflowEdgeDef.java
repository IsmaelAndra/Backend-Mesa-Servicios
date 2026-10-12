package com.mesaayuda.workflow;

import java.util.List;
import java.util.Map;

/**
 * Conector entre dos nodos.
 *
 * <p>El {@code sourcePort} es lo que convierte esto en un grafo de verdad y no en
 * una lista: un IF tiene dos salidas (SI y NO) y un SWITCH tiene una por rama. Sin
 * identificar el puerto, las dos salidas del IF serían indistinguibles.
 *
 * @param from       id del nodo de origen
 * @param to         id del nodo de destino
 * @param sourcePort nombre del puerto de salida. Para un IF, "SI" o "NO"; para
 *                   un SWITCH, el valor por el que sale; para el resto, "NEXT"
 * @param label      texto que se pinta sobre la línea
 */
public record WorkflowEdgeDef(
        String from,
        String to,
        String sourcePort,
        String label
) {
    /** Puerto de salida por defecto, para nodos con una sola vía. */
    public static final String PUERTO_UNICO = "NEXT";

    public String puerto() {
        return sourcePort == null || sourcePort.isBlank() ? PUERTO_UNICO : sourcePort;
    }
}