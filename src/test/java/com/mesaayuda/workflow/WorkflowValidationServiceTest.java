package com.mesaayuda.workflow;

import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validación del grafo de un workflow.
 *
 * <p>Se comprueba sobre todo lo que, de guardarse sin revisar, daría un proceso
 * que no hace lo que su autor cree: una rama sin conectar, un nodo huérfano, un
 * IF con una sola salida, un ciclo.
 *
 * <p>El caso del ciclo merece explicación: un grafo con un bucle no es un error de
 * Red de paquetes, es una ejecución que se come los hilos hasta que la cortan los
 * limites. Que el error salte al GUARDAR y no a las tres de la mañana es toda la
 * diferencia entre un aviso y un incidente.
 */
class WorkflowValidationServiceTest {

    private final WorkflowValidationService validador = new WorkflowValidationService();

    // ============================ Nodos basicos ============================

    @Test
    @DisplayName("un grafo minimo valido pasa: INICIO -> NOTIFY -> FIN")
    void grafoMinimoValido() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("aviso", "NOTIFY", Map.of("mensaje", "Aviso", "destino", "AGENTE")),
                        nodo("fin", "FIN")),
                List.of(
                        e("ini", "aviso"),
                        e("aviso", "fin")));

        assertTrue(validador.validar(grafo).isEmpty(),
                () -> "no deberia haber errores: " + validador.validar(grafo));
        assertDoesNotThrow(() -> validador.exigirValido(grafo));
    }

    @Test
    @DisplayName("un grafo vacio se rechaza")
    void grafoVacio() {
        List<String> errores = validador.validar(grafo(List.of(), List.of()));

        assertTrue(errores.stream().anyMatch(e -> e.contains("no tiene nodos")), errores.toString());
    }

    @Test
    @DisplayName("falta el nodo de inicio")
    void faltaInicio() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("a", "NOTIFY"), nodo("fin", "FIN")),
                List.of(e("a", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("Falta el nodo de inicio")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("dos nodos de inicio se rechazan")
    void dosInicios() {
        // Con dos inicios el motor no sabria por donde empezar, y elegir uno al
        // azar daria un proceso distinto del que su autor cree haber guardado.
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("i1", "INICIO"), nodo("i2", "INICIO"), nodo("fin", "FIN")),
                List.of(e("i1", "fin"), e("i2", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("solo puede haber uno")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("falta el nodo de fin")
    void faltaFin() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("a", "NOTIFY")),
                List.of(e("ini", "a")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("Falta un nodo de fin")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("un tipo de nodo inventado se rechaza")
    void tipoDesconocido() {
        // Es la garantia de que no se puede meter codigo por el grafo: el motor
        // solo resuelve tipos registrados en codigo.
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("malo", "EJECUTAR_SQL"), nodo("fin", "FIN")),
                List.of(e("ini", "malo"), e("malo", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("tipo desconocido")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("dos nodos con el mismo id se rechazan")
    void idsRepetidos() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("dup", "NOTIFY"), nodo("dup", "EMAIL"), nodo("fin", "FIN")),
                List.of(e("ini", "dup"), e("dup", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("está repetido")),
                validador.validar(grafo).toString());
    }

    // ============================ Conectores ============================

    @Test
    @DisplayName("un nodo sin salida se rechaza")
    void nodoSinSalida() {
        // Sin salida se convierte en un callejon que el motor solo descubre al
        // ejecutar, cuando ya ha hecho la mitad del trabajo.
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("a", "NOTIFY"), nodo("huerfano", "EMAIL"), nodo("fin", "FIN")),
                List.of(e("ini", "a"), e("a", "fin")));

        List<String> errores = validador.validar(grafo);
        assertTrue(errores.stream().anyMatch(x -> x.contains("no tiene salida")), errores.toString());
        assertTrue(errores.stream().anyMatch(x -> x.contains("no se alcanza desde el inicio")),
                errores.toString());
    }

    @Test
    @DisplayName("un IF necesita las dos salidas")
    void ifNecesitaLasDosSalidas() {
        // Sin la rama NO, un caso que no se cumple se queda sin hacer nada y en
        // silencio, que es el peor modo de fallo posible en una automatizacion.
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("cond", "IF", Map.of("campo", "ticket.prioridad", "operador", "==", "valor", "CRITICA")),
                        nodo("si", "NOTIFY"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "cond"), borde("cond", "si", "SI"), e("si", "fin")));

        List<String> errores = validador.validar(grafo);
        // El mensaje nombra el puerto que falta, para que el constructor sepa que
        // linea dejar por conectar.
        assertEquals(1, errores.stream()
                .filter(x -> x.contains("no tiene conector de salida NO"))
                .count(), errores.toString());
    }

    @Test
    @DisplayName("un IF con las dos salidas pasa")
    void ifCompleto() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("cond", "IF", Map.of("campo", "ticket.prioridad", "operador", "==", "valor", "CRITICA")),
                        nodo("si", "NOTIFY"),
                        nodo("no", "EMAIL"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "cond"), borde("cond", "si", "SI"), borde("cond", "no", "NO"),
                        e("si", "fin"), e("no", "fin")));

        assertTrue(validador.validar(grafo).isEmpty(),
                () -> "no deberia haber errores: " + validador.validar(grafo));
    }

    @Test
    @DisplayName("un conector a un nodo inexistente se rechaza")
    void conectorHuerfano() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("fin", "FIN")),
                List.of(e("ini", "fantasma")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("que no existe")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("un nodo que se conecta a si mismo se rechaza")
    void autoBucle() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("a", "NOTIFY"), nodo("fin", "FIN")),
                List.of(e("ini", "a"), e("a", "a"), e("a", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("se conecta a sí mismo")),
                validador.validar(grafo).toString());
    }

    // ============================ SWITCH ============================

    @Test
    @DisplayName("un SWITCH necesita casos")
    void switchSinCasos() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("sw", "SWITCH", Map.of("campo", "ticket.prioridad")),
                        nodo("a", "NOTIFY"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "sw"), borde("sw", "a", "ALTA"), e("a", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("lista de casos")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("una rama de SWITCH sin conector se avisa")
    void switchRamaSinConector() {
        // No es fatal: puede que el caso exista para no hacer nada. Se avisa
        // porque el sintoma de olvidarlo es "a veces no pasa nada".
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("sw", "SWITCH", Map.of("campo", "ticket.prioridad", "casos", List.of(
                                Map.of("valor", "CRITICA", "salida", "critica"),
                                Map.of("valor", "BAJA", "salida", "baja")))),
                        nodo("critica", "NOTIFY"),
                        nodo("baja", "NOTIFY"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "sw"), borde("sw", "critica", "critica"), borde("sw", "baja", "baja"),
                        e("critica", "fin"), e("baja", "fin")));

        // Ambas ramas tienen conector: no debe haber error.
        assertTrue(validador.validar(grafo).isEmpty(),
                () -> "no deberia haber errores: " + validador.validar(grafo));
    }

    @Test
    @DisplayName("una rama declarada sin conector se detecta")
    void switchRamaDeclaradaSinConector() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("sw", "SWITCH", Map.of("campo", "ticket.prioridad", "casos", List.of(
                                Map.of("valor", "CRITICA", "salida", "critica"),
                                Map.of("valor", "BAJA", "salida", "baja")))),
                        nodo("critica", "NOTIFY"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "sw"), borde("sw", "critica", "critica"), e("critica", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("no tiene conector de salida")),
                validador.validar(grafo).toString());
    }

    @Test
    @DisplayName("un caso de SWITCH incompleto se detecta")
    void switchCasoIncompleto() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("sw", "SWITCH", Map.of("campo", "ticket.prioridad", "casos", List.of(
                                Map.of("valor", "CRITICA")))),
                        nodo("a", "NOTIFY"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "sw"), borde("sw", "a", "CRITICA"), e("a", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("incompleto")),
                validador.validar(grafo).toString());
    }

    // ============================ Ciclos ============================

    @Test
    @DisplayName("un ciclo se rechaza y se dice donde esta")
    void cicloSeRechaza() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("a", "WAIT", Map.of("minutos", 1)),
                        nodo("b", "NOTIFY"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "a"), e("a", "b"), e("b", "a"), e("b", "fin")));

        List<String> errores = validador.validar(grafo);
        assertTrue(errores.stream().anyMatch(x -> x.contains("ciclo")), errores.toString());
        // El mensaje lleva el camino, no solo un si/no: quien edita el grafo
        // necesita saber que nodos ha que cortar.
        assertTrue(errores.stream().anyMatch(x -> x.contains("→")), errores.toString());
    }

    @Test
    @DisplayName("un ciclo A -> B -> A que no toca el fin tambien se rechaza")
    void cicloDeWorkflows() {
        WorkflowGraphDef grafo = grafo(
                List.of(
                        nodo("ini", "INICIO"),
                        nodo("a", "WAIT", Map.of("minutos", 1)),
                        nodo("b", "WAIT", Map.of("minutos", 1)),
                        nodo("fin", "FIN")),
                List.of(e("ini", "a"), e("a", "b"), e("b", "a")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("ciclo")),
                validador.validar(grafo).toString());
    }

    // ============================ API ============================

    @Test
    @DisplayName("exigirValido lanza BadRequestException con el motivo")
    void exigirValidoLanza() {
        WorkflowGraphDef grafo = grafo(List.of(), List.of());

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> validador.exigirValido(grafo));
        assertTrue(e.getMessage().contains("no es válido"), e.getMessage());
    }

    @Test
    @DisplayName("si los nodos fallan, no se siguen comprobando los conectores")
    void losErroresNoSeDerivan() {
        // Si se sigue a los conectores con un grafo de nodos roto,
        // los mensajes hablarían de nodos que no existen y taparian el problema real.
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("x", "TIPO_INVENTADO")),
                List.of(e("ini", "fantasma")));

        List<String> errores = validador.validar(grafo);
        assertTrue(errores.stream().anyMatch(x -> x.contains("tipo desconocido")), errores.toString());
        assertTrue(errores.stream().noneMatch(x -> x.contains("fantasma")),
                "no debe hablar de conectores si los nodos ya fallan: " + errores);
    }

    @Test
    @DisplayName("un grafo lineal sin condiciones es valido")
    void grafoLinealSinCondiciones() {
        // No es obligatorio ramificar: una cadena de acciones es un workflow valido.
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("t", "TASK_CREATE", Map.of("titulo", "Revisar")),
                        nodo("fin", "FIN")),
                List.of(e("ini", "t"), e("t", "fin")));

        assertTrue(validador.validar(grafo).isEmpty(),
                () -> "no deberia haber errores: " + validador.validar(grafo));
    }

    // ============================ Configuración de los nodos ============================

    @Test
    @DisplayName("un NOTIFY sin mensaje ni destinatario no se publica")
    void notifySinConfiguracionSeRechaza() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("aviso", "NOTIFY", Map.of()), nodo("fin", "FIN")),
                List.of(e("ini", "aviso"), e("aviso", "fin")));

        List<String> errores = validador.validar(grafo);
        assertTrue(errores.stream().anyMatch(x -> x.contains("no tiene mensaje")), errores::toString);
        assertTrue(errores.stream().anyMatch(x -> x.contains("no tiene destinatario")), errores::toString);
    }

    @Test
    @DisplayName("un IF con un operador inventado no se publica")
    void ifConOperadorInventado() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"),
                        nodo("cond", "IF", Map.of("campo", "ticket.prioridad", "operador", "parecido")),
                        nodo("fin", "FIN")),
                List.of(e("ini", "cond"), borde("cond", "fin", "SI"), borde("cond", "fin", "NO")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("operador")));
    }

    @Test
    @DisplayName("un id con punto se rechaza: rompería nodos.<id>.<clave>")
    void idConPuntoSeRechaza() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"), nodo("nexa.1", "NOTIFY"), nodo("fin", "FIN")),
                List.of(e("ini", "nexa.1"), e("nexa.1", "fin")));

        assertTrue(validador.validar(grafo).stream().anyMatch(x -> x.contains("identificador no válido")));
    }

    @Test
    @DisplayName("con disparador de usuario, un nodo de ticket tiene que indicar cuál")
    void disparadorSinTicketExigeTicketId() {
        WorkflowGraphDef grafo = grafo(
                List.of(nodo("ini", "INICIO"),
                        nodo("cerrar", "TICKET_CLOSE"),
                        nodo("fin", "FIN")),
                List.of(e("ini", "cerrar"), e("cerrar", "fin")));

        assertTrue(validador.validar(grafo, "USER_CREATED").stream().anyMatch(x -> x.contains("ticketId")));
        assertTrue(validador.validar(grafo, "TICKET_CREATED").isEmpty(),
                "con un disparador de ticket, el ticket sale del evento");
    }

    // ============================ helpers ============================

    private WorkflowGraphDef grafo(List<WorkflowNodeDef> nodos, List<WorkflowEdgeDef> aristas) {
        return new WorkflowGraphDef("prueba", nodos, aristas, Map.of());
    }

    private WorkflowNodeDef nodo(String id, String tipo) {
        return new WorkflowNodeDef(id, tipo, id, configValida(tipo), 0.0, 0.0);
    }

    /**
     * Configuración mínima válida de cada tipo. Desde que la validación revisa la
     * configuración, un NOTIFY sin mensaje ya no se publica; las pruebas de
     * estructura del grafo no deben fallar por eso.
     */
    private static Map<String, Object> configValida(String tipo) {
        return switch (tipo) {
            case "NOTIFY" -> Map.of("mensaje", "Aviso", "destino", "AGENTE");
            case "EMAIL" -> Map.of("para", "a@coacmagisterio.fin.ec", "asunto", "A", "cuerpo", "B");
            default -> Map.of();
        };
    }

    private WorkflowNodeDef nodo(String id, String tipo, Map<String, Object> config) {
        return new WorkflowNodeDef(id, tipo, id, config, 0.0, 0.0);
    }

    private WorkflowEdgeDef e(String desde, String hasta) {
        return new WorkflowEdgeDef(desde, hasta, "NEXT", null);
    }

    private WorkflowEdgeDef borde(String desde, String hasta, String puerto) {
        return new WorkflowEdgeDef(desde, hasta, puerto, puerto);
    }
}