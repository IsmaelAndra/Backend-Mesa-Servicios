package com.mesaayuda.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El contrato JSON del grafo, tal y como viaja entre el constructor visual y el
 * servidor.
 *
 * <p>Estas pruebas existen por un fallo concreto que costó tiempo: un cliente
 * enviaba {@code port}, {@code x} e {@code label}, y el servidor no se quejaba
 * porque Jackson descarta en silencio lo que no reconoce. El grafo llegaba vacío
 * y el error que salía era "El grafo no tiene nodos", que no señalaba el
 * problema real. Peor aún, en el caso del puerto, el nodo IF llegaba sin salidas y
 * el mensaje hablaba de conectores, no de nombres de campos.
 *
 * <p>Un constructor visual es quien va a producir este JSON, así que el contrato
 * queda fijado aquí a propósito. Si alguien renombra un componente del record,
 * estas pruebas fallan antes de que un usuario pierda una tarde.
 */
class WorkflowGraphDefContratoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** El JSON tal y como lo produciría el lienzo del constructor. */
    private static final String JSON_DEL_CLIENTE = """
            {
              "name": "Escalamiento",
              "nodes": [
                {"id":"ini","type":"INICIO","name":"Inicio","config":{},
                 "positionX":0,"positionY":0},
                {"id":"revisar","type":"IF","name":"¿Critico?",
                 "config":{"campo":"ticket.prioridad","operador":"==","valor":"CRITICA"},
                 "positionX":200,"positionY":0},
                {"id":"fin","type":"FIN","name":"Fin","config":{},
                 "positionX":400,"positionY":0}
              ],
              "edges": [
                {"from":"ini","to":"revisar","sourcePort":"NEXT","label":null},
                {"from":"revisar","to":"fin","sourcePort":"SI","label":"si"},
                {"from":"revisar","to":"fin","sourcePort":"NO","label":"no"}
              ],
              "variables": {}
            }
            """;

    @Test
    @DisplayName("el grafo del cliente se lee con nodos, conectores y variables")
    void leeElGrafoDelCliente() throws Exception {
        WorkflowGraphDef g = mapper.readValue(JSON_DEL_CLIENTE, WorkflowGraphDef.class);

        assertEquals(3, g.nodos().size());
        assertEquals(3, g.conectores().size());
        assertEquals("Escalamiento", g.name());
        assertNotNull(g.nodoInicio());
        assertEquals("ini", g.nodoInicio().id());
    }

    @Test
    @DisplayName("el puerto del conector se lee de sourcePort y vale SI o NO")
    void leeLosPuertosDelIf() throws Exception {
        // Si esto falla, un IF llega al motor sin salidas y la rama que no se
        // cumple se pierde en silencio.
        WorkflowGraphDef g = mapper.readValue(JSON_DEL_CLIENTE, WorkflowGraphDef.class);

        assertEquals(1, g.salidasDe("revisar", "SI").size());
        assertEquals(1, g.salidasDe("revisar", "NO").size());
        assertEquals(1, g.salidasDe("ini", "NEXT").size());
    }

    @Test
    @DisplayName("un puerto ausente vale NEXT en vez de null")
    void puertoAusenteValeNext() throws Exception {
        // El motor compara el puerto con una cadena; un null Comparado con "NEXT"
        // no coincidiría y el nodo quedaría sin salidarecognized.
        WorkflowGraphDef g = mapper.readValue(
                "{\"nodes\":[{\"id\":\"a\",\"type\":\"INICIO\"},{\"id\":\"b\",\"type\":\"FIN\"}],"
                        + "\"edges\":[{\"from\":\"a\",\"to\":\"b\"}]}",
                WorkflowGraphDef.class);

        assertEquals("NEXT", g.conectores().get(0).puerto());
    }

    @Test
    @DisplayName("el nombre y la posicion del nodo no se pierden al guardar")
    void conservaNombreYPosicion() throws Exception {
        // El constructor reabre el workflow donde se dejó. Si la posicion se
        // perdiera, todas las cajas aparecerían apiladas en el origen.
        WorkflowGraphDef g = mapper.readValue(JSON_DEL_CLIENTE, WorkflowGraphDef.class);
        WorkflowNodeDef inicio = g.nodos().get(0);

        assertEquals("Inicio", inicio.name());
        assertEquals(0.0, inicio.positionX());
        assertEquals(0.0, inicio.positionY());
    }

    @Test
    @DisplayName("el grafo se vuelve a escribir con los mismos nombres")
    void idaYVuelta() throws Exception {
        WorkflowGraphDef original = mapper.readValue(JSON_DEL_CLIENTE, WorkflowGraphDef.class);

        String escrito = mapper.writeValueAsString(original);
        WorkflowGraphDef releido = mapper.readValue(escrito, WorkflowGraphDef.class);

        // Ida y vuelta sin perdidas: lo que se guarda y se vuelve a abrir tiene
        // que ser el mismo grafo, no uno degradado.
        assertEquals(original.nodos().size(), releido.nodos().size());
        assertEquals(original.conectores().size(), releido.conectores().size());
        assertEquals(original.nodoInicio().name(), releido.nodoInicio().name());
        assertEquals(original.salidasDe("revisar", "SI").size(),
                releido.salidasDe("revisar", "SI").size());
        assertTrue(escrito.contains("\"positionX\""));
        assertTrue(escrito.contains("\"sourcePort\""));
    }

    @Test
    @DisplayName("un grafo con IF completo pasa la validacion")
    void grafoConIfCompletoEsValido() {
        // El contrato y la validacion juntos: el ejemplo del constructor tiene que
        // ser publicable, o el constructor honnara algo que el servidor rechaza.
        WorkflowValidationService validador = new WorkflowValidationService();
        WorkflowGraphDef g;
        try {
            g = mapper.readValue(JSON_DEL_CLIENTE, WorkflowGraphDef.class);
        } catch (Exception e) {
            throw new AssertionError("el JSON de ejemplo deberia ser valido", e);
        }

        assertEquals(List.of(), validador.validar(g),
                "el grafo de ejemplo no deberia tener errores: " + validador.validar(g));
    }

    @Test
    @DisplayName("un IF al que le falta la salida NO dice cual falta")
    void ifSinSalidaNoDiceCual() {
        WorkflowValidationService validador = new WorkflowValidationService();
        WorkflowGraphDef g = new WorkflowGraphDef("g",
                List.of(
                        new WorkflowNodeDef("ini", "INICIO", "Inicio", Map.of(), 0.0, 0.0),
                        new WorkflowNodeDef("revisar", "IF", "¿Critico?", Map.of("campo", "ticket.prioridad", "operador", "==", "valor", "CRITICA"), 200.0, 0.0),
                        new WorkflowNodeDef("fin", "FIN", "Fin", Map.of(), 400.0, 0.0)),
                List.of(
                        new WorkflowEdgeDef("ini", "revisar", "NEXT", null),
                        new WorkflowEdgeDef("revisar", "fin", "SI", "si")),
                Map.of());

        List<String> errores = validador.validar(g);

        assertEquals(1, errores.size(), "un solo error, no el mismo repetido dos veces");
        assertTrue(errores.get(0).contains("NO"),
                "el mensaje debe nombrar el puerto que falta: " + errores);
    }
}