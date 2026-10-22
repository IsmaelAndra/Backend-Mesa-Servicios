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

    @Test
    void elGrafoNuevoAplicaElTipo() {
        WorkflowGraphDef grafo = WorkflowsDeFabrica.grafoClasificacion();
        assertTrue(grafo.nodes().stream().anyMatch(n -> "TICKET_TYPE".equals(n.type())));
    }

    @Test
    void soloSeActualizaLaVersionOriginalSinCambios() {
        String v1 = "{\"name\":\"x\",\"nodes\":["
                + "{\"id\":\"inicio\",\"type\":\"INICIO\"},{\"id\":\"nexa\",\"type\":\"NEXA\"},"
                + "{\"id\":\"segura\",\"type\":\"IF\"},{\"id\":\"hayPrioridad\",\"type\":\"IF\"},"
                + "{\"id\":\"prioridad\",\"type\":\"TICKET_PRIORITY\"},{\"id\":\"hayCategoria\",\"type\":\"IF\"},"
                + "{\"id\":\"categoria\",\"type\":\"TICKET_CATEGORY\"},{\"id\":\"nota\",\"type\":\"TICKET_COMMENT\"},"
                + "{\"id\":\"fin\",\"type\":\"FIN\"}],\"edges\":[]}";
        assertTrue(WorkflowsDeFabrica.esVersion1(v1));
        // Con un nodo más (alguien la modificó) no se toca.
        String modificada = v1.replace("{\"id\":\"fin\"", "{\"id\":\"aviso\",\"type\":\"NOTIFY\"},{\"id\":\"fin\"");
        org.junit.jupiter.api.Assertions.assertFalse(WorkflowsDeFabrica.esVersion1(modificada));
        org.junit.jupiter.api.Assertions.assertFalse(WorkflowsDeFabrica.esVersion1("no es json"));
    }

    @Test
    void alRenombrarAMapiSoloCambiaElTextoVisible() {
        String v = "{\"name\":\"x\",\"nodes\":["
                + "{\"id\":\"nexa\",\"type\":\"NEXA\",\"name\":\"NEXA clasifica\",\"config\":{\"accion\":\"CLASIFICAR\"}},"
                + "{\"id\":\"nota\",\"type\":\"TICKET_COMMENT\",\"name\":\"Nota\","
                + "\"config\":{\"mensaje\":\"NEXA: {{nodos.nexa.resumen}}\",\"interno\":true}}],\"edges\":[]}";
        WorkflowGraphDef g = WorkflowsDeFabrica.conTextosMapi(v);
        org.junit.jupiter.api.Assertions.assertNotNull(g);
        WorkflowNodeDef nexa = g.nodes().get(0);
        org.junit.jupiter.api.Assertions.assertEquals("NEXA", nexa.type(), "el tipo de nodo no cambia");
        org.junit.jupiter.api.Assertions.assertEquals("nexa", nexa.id(), "el id tampoco");
        org.junit.jupiter.api.Assertions.assertEquals("MAPI clasifica", nexa.name());
        org.junit.jupiter.api.Assertions.assertEquals("MAPI: {{nodos.nexa.resumen}}", g.nodes().get(1).config().get("mensaje"));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, g.nodes().get(1).config().get("interno"));
        // Sin nada que cambiar no se publica versión nueva.
        org.junit.jupiter.api.Assertions.assertNull(WorkflowsDeFabrica.conTextosMapi(v.replace("NEXA clasifica", "MAPI clasifica").replace("NEXA:", "MAPI:")));
    }
}
