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
    void elGrafoDeFabricaUsaMapi() {
        WorkflowGraphDef grafo = WorkflowsDeFabrica.grafoClasificacion();
        assertTrue(grafo.nodes().stream().anyMatch(n -> "mapi".equals(n.id()) && "MAPI".equals(n.type())));
        assertTrue(grafo.nodes().stream().noneMatch(n -> String.valueOf(n.config()).contains("nexa")));
    }

    @Test
    void unaVersionConNexaSeConvierteEnteraAMapi() {
        String v = "{\"name\":\"Flujo NEXA\",\"nodes\":["
                + "{\"id\":\"inicio\",\"type\":\"INICIO\",\"name\":\"Inicio\",\"config\":{}},"
                + "{\"id\":\"nexa\",\"type\":\"NEXA\",\"name\":\"NEXA clasifica\",\"config\":{\"accion\":\"CLASIFICAR\"}},"
                + "{\"id\":\"segura\",\"type\":\"IF\",\"name\":\"¿Segura?\",\"config\":{\"campo\":\"nodos.nexa.confianza\",\"valor\":0.6}},"
                + "{\"id\":\"nota\",\"type\":\"TICKET_COMMENT\",\"name\":\"Nota\","
                + "\"config\":{\"mensaje\":\"NEXA: {{nodos.nexa.resumen}} {{nexaResultado}}\",\"interno\":true}}],"
                + "\"edges\":[{\"from\":\"inicio\",\"to\":\"nexa\",\"sourcePort\":\"NEXT\"},"
                + "{\"from\":\"nexa\",\"to\":\"segura\",\"sourcePort\":\"NEXT\"}]}";
        WorkflowGraphDef g = WorkflowsDeFabrica.aMapi(v);
        org.junit.jupiter.api.Assertions.assertNotNull(g);
        WorkflowNodeDef mapi = g.nodes().get(1);
        org.junit.jupiter.api.Assertions.assertEquals("MAPI", mapi.type());
        org.junit.jupiter.api.Assertions.assertEquals("mapi", mapi.id());
        org.junit.jupiter.api.Assertions.assertEquals("MAPI clasifica", mapi.name());
        org.junit.jupiter.api.Assertions.assertEquals("nodos.mapi.confianza", g.nodes().get(2).config().get("campo"));
        org.junit.jupiter.api.Assertions.assertEquals(0.6, g.nodes().get(2).config().get("valor"));
        org.junit.jupiter.api.Assertions.assertEquals("MAPI: {{nodos.mapi.resumen}} {{mapiResultado}}",
                g.nodes().get(3).config().get("mensaje"));
        org.junit.jupiter.api.Assertions.assertEquals("mapi", g.edges().get(0).to());
        org.junit.jupiter.api.Assertions.assertEquals("mapi", g.edges().get(1).from());
        org.junit.jupiter.api.Assertions.assertEquals("Flujo MAPI", g.name());
        // El resultado ya convertido no cambia más: no se publica otra versión.
        org.junit.jupiter.api.Assertions.assertNull(WorkflowsDeFabrica.aMapi(
                "{\"name\":\"x\",\"nodes\":[{\"id\":\"mapi\",\"type\":\"MAPI\",\"name\":\"MAPI\",\"config\":{}}],\"edges\":[]}"));
    }

    @Test
    void elTipoViejoNexaSeResuelveComoMapi() {
        org.junit.jupiter.api.Assertions.assertEquals(WorkflowNodeType.MAPI, WorkflowNodeType.desde("NEXA"));
        org.junit.jupiter.api.Assertions.assertEquals(WorkflowNodeType.MAPI, WorkflowNodeType.desde("mapi"));
    }
}
