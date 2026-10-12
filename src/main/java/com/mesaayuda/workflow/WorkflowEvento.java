package com.mesaayuda.workflow;

import java.util.UUID;
import java.util.Map;

/**
 * Un hecho del sistema que puede disparar workflows.
 *
 * @param tipo         disparador ({@link WorkflowTriggerType})
 * @param clave        identificador único del hecho, para la deduplicación
 * @param datos        lo que se sabía en el momento del hecho (queda como {@code evento.*})
 * @param entidadTipo  TICKET, USUARIO o null
 * @param entidadId    id de esa entidad
 * @param origen       ejecución de workflow que provocó el hecho, o null si no lo provocó ninguna
 */
public record WorkflowEvento(String tipo,
                             String clave,
                             Map<String, Object> datos,
                             String entidadTipo,
                             UUID entidadId,
                             WorkflowOrigen.Datos origen) {
}
