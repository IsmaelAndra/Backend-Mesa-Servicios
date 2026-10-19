package com.mesaayuda.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La automatización de fábrica tiene que pasar el mismo validador que el constructor. */
class WorkflowsDeFabricaTest {

    private final WorkflowValidationService validador = new WorkflowValidationService();

    @Test
    void laClasificacionConIaEsUnGrafoValido() {
        WorkflowGraphDef grafo = WorkflowsDeFabrica.grafoClasificacion();

        assertTrue(validador.validar(grafo).isEmpty(), () -> "errores: " + validador.validar(grafo));
        assertDoesNotThrow(() -> validador.exigirValido(grafo, WorkflowTriggerType.TICKET_CREATED.name()));
    }
}
