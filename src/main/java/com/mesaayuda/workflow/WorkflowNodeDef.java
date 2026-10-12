package com.mesaayuda.workflow;

import java.util.List;
import java.util.Map;

/**
 * Nodo del grafo, tal y como viaja en el JSON de la definición.
 *
 * <p>Es un record y no una entidad a propósito: un grafo se lee y se escribe
 * entero, nunca se consulta por nodos sueltos. Normalizarlo en tablas daría
 * joins para reconstruir algo que siempre se usa junto.
 *
 * @param id        identificador único dentro del grafo. No es el tipo: un grafo
 *                  puede tener dos nodos NOTIFY y en el historial tienen que poder
 *                  distinguirse
 * @param type      tipo de nodo. El motor solo acepta los tipos registrados; uno
 *                  inventado se rechaza al guardar
 * @param name      etiqueta que se pinta en el canvas
 * @param config    configuración del nodo. Su forma la declara el handler de su
 *                  tipo, no esta clase
 * @param positionX coordenada X en el canvas, para reponer el dibujo al guardar
 * @param positionY coordenada Y
 */
public record WorkflowNodeDef(
        String id,
        String type,
        String name,
        Map<String, Object> config,
        Double positionX,
        Double positionY
) {
    public WorkflowNodeDef {
        if (config == null) {
            config = Map.of();
        }
    }

    /** Texto de la etiqueta, o el id si no tiene. */
    public String etiqueta() {
        return name == null || name.isBlank() ? id : name;
    }
}