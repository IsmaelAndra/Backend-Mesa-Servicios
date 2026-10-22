package com.mesaayuda.workflow;

import com.mesaayuda.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valida el grafo de un workflow antes de guardarlo.
 *
 * <p>Se valida aquí y no en el motor por dos razones: un workflow que no ejecuta
 * es un workflow que no ha roto la producción todavía, y un constructor visual que
 * avisa al instante es usable, mientras que un fallo tres días después no.
 *
 * <p>Las comprobaciones van de lo estructural a lo de contenido. Primero que el
 * grafo tenga forma; después que los nodos tengan lo que necesitan. Un error de
 * contenido sin un error de estructura antes produce mensajes que no ayudan.
 */
@Service
public class WorkflowValidationService {

    /**
     * Valida y devuelve los problemas encontrados.
     *
     * @return lista vacía si todo cuadra
     */
    public List<String> validar(WorkflowGraphDef grafo) {
        List<String> errores = new ArrayList<>();
        if (grafo == null) {
            errores.add("La definición del workflow está vacía.");
            return errores;
        }

        validarNodos(grafo, errores);
        if (!errores.isEmpty()) {
            // Si los nodos no están bien, seguir con los conectores daría errores
            // derivados que distraen del problema real.
            return errores;
        }
        validarConectores(grafo, errores);
        validarAlcanzables(grafo, errores);
        validarAciclicidad(grafo, errores);
        validarConfiguracion(grafo, errores);
        return errores;
    }

    /**
     * Igual que {@link #validar(WorkflowGraphDef)}, sabiendo además el disparador:
     * un workflow de "Usuario creado" o programado no trae ticket, así que los
     * nodos que trabajan sobre un ticket tienen que indicar cuál.
     */
    public List<String> validar(WorkflowGraphDef grafo, String disparador) {
        List<String> errores = validar(grafo);
        WorkflowTriggerType tipo = WorkflowTriggerType.desde(disparador);
        if (grafo == null || tipo == null || tipo.entidad() == WorkflowTriggerType.Entidad.TICKET
                || tipo == WorkflowTriggerType.MANUAL) {
            return errores;
        }
        for (WorkflowNodeDef n : grafo.nodos()) {
            WorkflowNodeType t = WorkflowNodeType.desde(n.type());
            if (t != null && NODOS_CON_TICKET.contains(t) && vacio(n, "ticketId")) {
                errores.add("El nodo '" + nombre(n) + "' trabaja sobre un ticket, pero el disparador '"
                        + tipo.descripcion() + "' no trae ninguno: indique ticketId (por ejemplo "
                        + "{{nodos.<id>.ticketId}} de un TICKET_CREATE anterior).");
            }
        }
        return errores;
    }

    /** Valida y lanza si hay problemas. Es la puerta que usa el servicio. */
    public void exigirValido(WorkflowGraphDef grafo) {
        exigirValido(grafo, null);
    }

    public void exigirValido(WorkflowGraphDef grafo, String disparador) {
        List<String> errores = disparador == null ? validar(grafo) : validar(grafo, disparador);
        if (!errores.isEmpty()) {
            throw new BadRequestException("El workflow no es válido: "
                    + String.join(" ", errores));
        }
    }

    // ============================ CONFIGURACIÓN ============================

    /** Nodos que actúan sobre un ticket y lo toman del evento si no se les indica. */
    private static final Set<WorkflowNodeType> NODOS_CON_TICKET = Set.of(
            WorkflowNodeType.TICKET_ASSIGN, WorkflowNodeType.TICKET_ASSIGN_TEAM, WorkflowNodeType.TICKET_STATUS,
            WorkflowNodeType.TICKET_COMMENT, WorkflowNodeType.TICKET_PRIORITY, WorkflowNodeType.TICKET_CATEGORY,
            WorkflowNodeType.TICKET_TYPE,
            WorkflowNodeType.TICKET_CLOSE, WorkflowNodeType.TICKET_REOPEN, WorkflowNodeType.SLA_APPLY,
            WorkflowNodeType.NEXA);

    private static final java.util.regex.Pattern ID_VALIDO = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,60}$");

    private static final Set<String> OPERADORES = Set.of("==", "!=", ">", "<", ">=", "<=",
            "contiene", "empieza_con", "termina_con", "vacio", "no_vacio", "es_verdadero", "es_falso");

    /**
     * Lo que cada tipo de nodo necesita en su configuración.
     *
     * <p>Antes un NOTIFY sin mensaje o un IF sin campo se publicaban sin queja y
     * fallaban en la primera ejecución real, días después. El constructor llama a
     * la misma validación con el botón "Validar", así que el error se ve al diseñar.
     */
    private void validarConfiguracion(WorkflowGraphDef grafo, List<String> errores) {
        for (WorkflowNodeDef n : grafo.nodos()) {
            if (n.id() != null && !ID_VALIDO.matcher(n.id()).matches()) {
                // Los ids se usan en nodos.<id>.<clave>: un punto partiría la ruta.
                errores.add("El nodo '" + n.id() + "' tiene un identificador no válido: solo letras, números, "
                        + "guion y guion bajo (máximo 60).");
            }
            WorkflowNodeType t = WorkflowNodeType.desde(n.type());
            if (t == null) {
                continue;
            }
            String nom = nombre(n);
            switch (t) {
                case IF -> {
                    if (vacio(n, "campo")) {
                        errores.add("La condición '" + nom + "' no indica qué campo compara.");
                    }
                    Object op = valor(n, "operador");
                    if (op == null || !OPERADORES.contains(String.valueOf(op))) {
                        errores.add("La condición '" + nom + "' tiene un operador no válido. Use uno de: " + OPERADORES);
                    }
                }
                case SWITCH -> {
                    if (vacio(n, "campo")) {
                        errores.add("El SWITCH '" + nom + "' no indica qué campo evalúa.");
                    }
                }
                case NOTIFY -> {
                    if (vacio(n, "mensaje")) {
                        errores.add("La notificación '" + nom + "' no tiene mensaje.");
                    }
                    if (vacio(n, "destino") && vacio(n, "usuarioId") && vacio(n, "correo") && vacio(n, "rol")) {
                        errores.add("La notificación '" + nom + "' no tiene destinatario (solicitante, agente, "
                                + "usuario, correo o rol).");
                    }
                }
                case EMAIL -> {
                    if (vacio(n, "para") || vacio(n, "asunto") || vacio(n, "cuerpo")) {
                        errores.add("El correo '" + nom + "' necesita destinatario, asunto y cuerpo.");
                    }
                }
                case TICKET_ASSIGN -> {
                    if (vacio(n, "agenteId") && vacio(n, "equipoId")) {
                        errores.add("La asignación '" + nom + "' no indica agente ni equipo.");
                    }
                }
                case TICKET_ASSIGN_TEAM -> {
                    if (vacio(n, "equipoId")) {
                        errores.add("La asignación a equipo '" + nom + "' no indica el equipo.");
                    }
                }
                case TICKET_STATUS -> {
                    if (vacio(n, "estado")) {
                        errores.add("El cambio de estado '" + nom + "' no indica el estado.");
                    }
                }
                case TICKET_PRIORITY -> {
                    if (vacio(n, "prioridadId")) {
                        errores.add("El cambio de prioridad '" + nom + "' no indica la prioridad.");
                    }
                }
                case TICKET_CATEGORY -> {
                    if (vacio(n, "categoriaId")) {
                        errores.add("El cambio de categoría '" + nom + "' no indica la categoría.");
                    }
                }
                case TICKET_TYPE -> {
                    if (vacio(n, "tipoId")) {
                        errores.add("El cambio de tipo '" + nom + "' no indica el tipo.");
                    }
                }
                case TICKET_COMMENT -> {
                    if (vacio(n, "mensaje")) {
                        errores.add("El comentario '" + nom + "' no tiene texto.");
                    }
                }
                case TICKET_CREATE -> {
                    if (vacio(n, "titulo") || vacio(n, "descripcion")) {
                        errores.add("La creación de ticket '" + nom + "' necesita título y descripción.");
                    }
                }
                case TASK_CREATE -> {
                    if (vacio(n, "titulo")) {
                        errores.add("La tarea '" + nom + "' no tiene título.");
                    }
                }
                case WAIT -> {
                    if (vacio(n, "minutos") && vacio(n, "hasta")) {
                        errores.add("La espera '" + nom + "' no indica minutos ni fecha.");
                    }
                }
                case APPROVAL -> {
                    // Solo decide un SUPER_ADMIN. Sin rol se entiende ese.
                    Object rol = valor(n, "rol");
                    if (rol != null && !String.valueOf(rol).isBlank()
                            && !"SUPER_ADMIN".equals(String.valueOf(rol).trim().toUpperCase(java.util.Locale.ROOT))) {
                        errores.add("La aprobación '" + nom + "' solo la puede decidir un SUPER_ADMIN.");
                    }
                }
                case NEXA -> {
                    Object accion = valor(n, "accion");
                    if (accion != null && com.mesaayuda.workflow.handler.NexaHandler.accion(String.valueOf(accion)) == null) {
                        errores.add("El nodo MAPI '" + nom + "' pide una acción que no existe. "
                                + "Use CLASIFICAR, RESUMIR o RESPONDER.");
                    }
                }
                default -> { }
            }
        }
    }

    private static Object valor(WorkflowNodeDef n, String clave) {
        return n.config() == null ? null : n.config().get(clave);
    }

    private static boolean vacio(WorkflowNodeDef n, String clave) {
        Object v = valor(n, clave);
        return v == null || String.valueOf(v).isBlank();
    }

    private static String nombre(WorkflowNodeDef n) {
        return n.name() == null || n.name().isBlank() ? n.id() : n.name();
    }

    // ============================ NODOS ============================

    private void validarNodos(WorkflowGraphDef grafo, List<String> errores) {
        List<WorkflowNodeDef> nodos = grafo.nodos();
        if (nodos.isEmpty()) {
            errores.add("El grafo no tiene nodos.");
            return;
        }

        Set<String> ids = new HashSet<>();
        for (WorkflowNodeDef n : nodos) {
            if (n.id() == null || n.id().isBlank()) {
                errores.add("Hay un nodo sin identificador.");
                continue;
            }
            if (!ids.add(n.id())) {
                errores.add("El nodo '" + n.id() + "' está repetido.");
            }
            WorkflowNodeType tipo = WorkflowNodeType.desde(n.type());
            if (tipo == null) {
                errores.add("El nodo '" + n.id() + "' tiene un tipo desconocido: " + n.type());
            }
        }

        long inicios = nodos.stream()
                .filter(n -> WorkflowGraphDef.TIPO_INICIO.equals(n.type())).count();
        if (inicios == 0) {
            errores.add("Falta el nodo de inicio.");
        } else if (inicios > 1) {
            // Con dos inicios el motor no sabe por dónde empezar, y elegirse uno al
            // azar daría un proceso distinto del que el administrador cree haber
            // guardado.
            errores.add("Hay " + inicios + " nodos de inicio; solo puede haber uno.");
        }

        if (nodos.stream().noneMatch(n -> WorkflowGraphDef.TIPO_FIN.equals(n.type()))) {
            errores.add("Falta un nodo de fin.");
        }

        if (nodos.stream().noneMatch(n -> WorkflowNodeType.desde(n.type()) == WorkflowNodeType.IF
                || WorkflowNodeType.desde(n.type()) == WorkflowNodeType.SWITCH)) {
            // No es un error: un workflow puede ser lineal. Se anota porque el
            // constructor puede avisar de que no hay condiciones.
            grafo.nodos().stream()
                    .filter(n -> WorkflowNodeType.desde(n.type()) == WorkflowNodeType.SWITCH)
                    .findFirst()
                    .ifPresent(n -> errores.add("El SWITCH '" + n.id() + "' no define sus ramas."));
        }
    }

    // ============================ CONECTORES ============================

    private void validarConectores(WorkflowGraphDef grafo, List<String> errores) {
        Set<String> idsNodo = new HashSet<>();
        grafo.nodos().forEach(n -> idsNodo.add(n.id()));

        Map<String, Set<String>> puertosUsados = new HashMap<>();

        for (WorkflowEdgeDef e : grafo.conectores()) {
            if (!idsNodo.contains(e.from())) {
                errores.add("Un conector sale de '" + e.from() + "', que no existe.");
                continue;
            }
            if (!idsNodo.contains(e.to())) {
                errores.add("Un conector llega a '" + e.to() + "', que no existe.");
                continue;
            }
            if (e.from().equals(e.to())) {
                errores.add("El nodo '" + e.from() + "' se conecta a sí mismo.");
                continue;
            }
            puertosUsados.computeIfAbsent(e.from(), k -> new HashSet<>()).add(e.puerto());
        }

        // Cada nodo, menos el FIN, tiene que tener salida. Un nodo sin salida se
        // convierte en un callejón sin salida que el motor interpreta como error en
        // tiempo de ejecución, cuando debería haberse detectado al guardar.
        for (WorkflowNodeDef n : grafo.nodos()) {
            WorkflowNodeType tipo = WorkflowNodeType.desde(n.type());
            if (tipo == null || tipo == WorkflowNodeType.FIN) {
                continue;
            }
            // Set.<String>of() explicito: ver la nota de validarRamasSwitch.
            Set<String> usados = puertosUsados.getOrDefault(n.id(), Set.<String>of());
            if (usados.isEmpty()) {
                errores.add("El nodo '" + n.id() + "' no tiene salida.");
                continue;
            }
if (tipo == WorkflowNodeType.IF) {
                for (String puerto : List.of("SI", "NO")) {
                    if (!usados.contains(puerto)) {
                        // Se nombra el puerto que falta, no se suelta el mismo
                        // mensaje dos veces. Con el constructor delante de uno, saber
                        // si falta SI o NO es justo lo que le deja arreglarlo.
                        errores.add("El nodo IF '" + n.id() + "' no tiene conector de salida "
                                + puerto + ".");
                    }
                }
            }
            if (tipo == WorkflowNodeType.SWITCH) {
                validarRamasSwitch(n, grafo, puertosUsados, errores);
            }
        }
    }

    private void validarRamasSwitch(WorkflowNodeDef n, WorkflowGraphDef grafo,
                                    Map<String, Set<String>> puertosUsados, List<String> errores) {
        Object bruto = n.config() == null ? null : n.config().get("casos");
        if (!(bruto instanceof List)) {
            errores.add("El SWITCH '" + n.id() + "' necesita una lista de casos.");
            return;
        }
        List<?> casos = (List<?>) bruto;
        if (casos.isEmpty()) {
            errores.add("El SWITCH '" + n.id() + "' necesita al menos un caso.");
            return;
        }

        // Set.<String>of() y no Set.of() a proposito: dentro de getOrDefault, un
        // Set.of() sin tipo explicito no infiere bien el tipo objetivo y javac
        // aborta la atribucion del bloque, con un "cannot find symbol" que
        // señala al simbolo equivocado (la variable del bucle de abajo) y hace
        // perder bastante tiempo buscando un error que no existe.
        Set<String> puertos = puertosUsados.getOrDefault(n.id(), Set.<String>of());
        for (Object elemento : casos) {
            if (!(elemento instanceof Map)) {
                continue;
            }
            Map<?, ?> mapa = (Map<?, ?>) elemento;
            Object valorCaso = mapa.get("valor");
            Object salida = mapa.get("salida");
            if (valorCaso == null || salida == null) {
                errores.add("Un caso del SWITCH '" + n.id() + "' está incompleto.");
                continue;
            }
            String puerto = String.valueOf(salida);
            if (!puertos.contains(puerto)) {
                // Una rama sin conectar no es un error fatal: puede ser un caso que
                // se quiere dejar sin hacer nada. Se avisa porque casi siempre es un
                // olvido, y el sintoma seria "a veces no pasa nada".
                errores.add("El caso '" + valorCaso + "' del SWITCH '" + n.id()
                        + "' no tiene conector de salida.");
            }
        }
    }

    private void validarAlcanzables(WorkflowGraphDef grafo, List<String> errores) {
        WorkflowNodeDef inicio = grafo.nodoInicio();
        if (inicio == null) {
            return;
        }

        Map<String, List<String>> adyacencia = new HashMap<>();
        grafo.conectores().forEach(e ->
                adyacencia.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to()));

        Set<String> visitados = new LinkedHashSet<>();
        Deque<String> pendientes = new ArrayDeque<>();
        pendientes.add(inicio.id());
        while (!pendientes.isEmpty()) {
            String actual = pendientes.poll();
            if (!visitados.add(actual)) {
                continue;
            }
            for (String siguiente : adyacencia.getOrDefault(actual, List.of())) {
                if (!visitados.contains(siguiente)) {
                    pendientes.add(siguiente);
                }
            }
        }

        for (WorkflowNodeDef n : grafo.nodos()) {
            if (!visitados.contains(n.id())) {
                // Nodos huérfanos no son un error fatal: puede que el administrador
                // esté a mitad de construir. Pero guardarlos sin avisar produce
                // workflows que luego no hacen lo que uno cree.
                errores.add("El nodo '" + n.id() + "' no se alcanza desde el inicio.");
            }
        }
    }

    // ============================ CICLOS ============================

    private void validarAciclicidad(WorkflowGraphDef grafo, List<String> errores) {
        Map<String, List<String>> adyacencia = new HashMap<>();
        grafo.conectores().forEach(e ->
                adyacencia.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to()));

        Set<String> enPila = new HashSet<>();
        Set<String> terminados = new HashSet<>();
        List<String> ciclo = new ArrayList<>();

        for (WorkflowNodeDef n : grafo.nodos()) {
            if (!terminados.contains(n.id()) && visitar(n.id(), adyacencia, enPila, terminados, ciclo)) {
                StringBuilder camino = new StringBuilder();
                for (int i = 0; i < ciclo.size(); i++) {
                    if (i > 0) {
                        camino.append(" → ");
                    }
                    camino.append(ciclo.get(i));
                }
                camino.append(" → ").append(ciclo.get(0));
                errores.add("El grafo tiene un ciclo: " + camino);
                return;
            }
        }
    }

    /** Recorrido en profundidad. Devuelve {@code true} si encontró un ciclo. */
    private boolean visitar(String nodo, Map<String, List<String>> adyacencia,
                            Set<String> enPila, Set<String> terminados, List<String> ciclo) {
        if (terminados.contains(nodo)) {
            return false;
        }
        if (!enPila.add(nodo)) {
            ciclo.add(nodo);
            return true;
        }
        for (String siguiente : adyacencia.getOrDefault(nodo, List.of())) {
            if (visitar(siguiente, adyacencia, enPila, terminados, ciclo)) {
                return true;
            }
        }
        enPila.remove(nodo);
        terminados.add(nodo);
        return false;
    }
}