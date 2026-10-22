package com.mesaayuda.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado mutable de una ejecución mientras el motor la recorre.
 *
 * <p>Una instancia vive durante todo el recorrido y se va llenando con lo que
 * cada nodo deja. Las variables son el único canal entre nodos: un nodo no puede
 * guardar un objeto suyo y que otro lo vea a través de un campo de esta clase, y
 * así no se acumulan referencias que nadie va a liberar ni se puede perder la
 * trazabilidad de qué cambió dónde.
 *
 * <p>El contexto no decide nada. Solo lleva la cuenta, para que el motor quede
 * escrito una vez y no se duplique por cada tipo de nodo.
 */
public class WorkflowContext {

    private final UUID executionId;
    private final UUID workflowId;
    private final UUID versionId;
    private final String correlationKey;
    private final int depth;
    private final Map<String, Object> variables = new LinkedHashMap<>();

    /** Nodo en el que está el motor ahora mismo. */
    private String currentNodeId;

    /** Pasos dados. Se cuenta para poder cortar un bucle. */
    private int pasos;

    public WorkflowContext(UUID executionId, UUID workflowId, UUID versionId,
                           String correlationKey, int depth) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.versionId = versionId;
        this.correlationKey = correlationKey;
        this.depth = depth;
    }

    /**
     * Lee una variable por nombre.
     *
     * <p>Admite ruta con puntos ({@code ticket.prioridad}) porque así se escriben
     * las condiciones en el constructor visual, y quien las escribe no debería
     * tener que saber que hay mapas anidados por medio.
     */
    /**
     * Valor de una variable.
     *
     * <p>{@code ticket.*} y {@code usuario.*} se resuelven en el momento contra la
     * base (ver {@link WorkflowVariables}); el resto sale del mapa de la ejecución.
     * Si el resolvedor no da nada, se mira también el mapa, que es donde estaban
     * esas claves en las ejecuciones anteriores a este cambio.
     */
    public Object leer(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        if (resolvedor != null && (ruta.startsWith("ticket.") || ruta.startsWith("usuario."))) {
            Object vivo = resolvedor.apply(ruta);
            if (vivo != null) {
                return vivo;
            }
        }
        return leerGuardada(ruta);
    }

    /** Valor del mapa de la ejecución, sin pasar por el resolvedor. */
    public Object leerGuardada(String ruta) {
        Object actual = variables;
        for (String parte : ruta.split("\\.")) {
            if (actual instanceof Map<?, ?> mapa) {
                actual = mapa.get(parte);
            } else {
                return null;
            }
        }
        return actual;
    }

    private static final java.util.regex.Pattern MARCADOR =
            java.util.regex.Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    /**
     * Sustituye cada {@code {{variable}}} del texto por su valor.
     *
     * <p>Es una sustitución, no un lenguaje: no hay expresiones, ni llamadas, ni
     * condicionales. Una variable que no existe queda vacía.
     */
    public String interpolar(String texto) {
        return interpolar(texto, false);
    }

    /**
     * Igual que {@link #interpolar(String)}, pero escapando los valores para HTML.
     * Es la que usa el correo: el título de un ticket lo escribe el solicitante, y
     * sin escapar podría meter HTML en un correo que sale con la firma de la casa.
     */
    public String interpolarHtml(String texto) {
        return interpolar(texto, true);
    }

    private String interpolar(String texto, boolean html) {
        if (texto == null || texto.indexOf("{{") < 0) {
            return texto;
        }
        java.util.regex.Matcher m = MARCADOR.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object valor = leer(m.group(1));
            String reemplazo = valor == null ? "" : String.valueOf(valor);
            if (html) {
                reemplazo = reemplazo.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        .replace("\"", "&quot;").replace("'", "&#39;");
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(reemplazo));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Texto de la configuración de un nodo con sus {@code {{variables}}} ya
     * sustituidas. Null si falta o queda vacío.
     */
    public String configTexto(WorkflowNodeDef nodo, String clave) {
        Object bruto = nodo.config() == null ? null : nodo.config().get(clave);
        if (bruto == null) {
            return null;
        }
        String valor = interpolar(String.valueOf(bruto));
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * Número de la configuración de un nodo. Admite un número, un texto con un
     * número o una variable ({@code {{nodos.mapi1.equipoId}}}). Null si no hay o
     * no es un número.
     */
    public Long configNumero(WorkflowNodeDef nodo, String clave) {
        Object bruto = nodo.config() == null ? null : nodo.config().get(clave);
        if (bruto instanceof Number n) {
            return n.longValue();
        }
        String texto = configTexto(nodo, clave);
        if (texto == null) {
            return null;
        }
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Identificador (UUID) de la configuración de un nodo: equipo, agente,
     * prioridad, categoría… Admite el UUID escrito o una variable
     * ({@code {{nodos.mapi1.equipoId}}}). Null si no hay o no es un UUID.
     *
     * <p>Desde V3__ids_uuid.sql los ids no son números: leerlos con
     * {@link #configNumero} devolvía siempre null y la acción fallaba.
     */
    public java.util.UUID configUuid(WorkflowNodeDef nodo, String clave) {
        Object bruto = nodo.config() == null ? null : nodo.config().get(clave);
        if (bruto instanceof java.util.UUID u) {
            return u;
        }
        String texto = configTexto(nodo, clave);
        if (texto == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(texto.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Escribe un resultado del nodo en {@code nodos.<id>.<clave>}, donde lo leen los nodos siguientes. */
    public void resultado(WorkflowNodeDef nodo, String clave, Object valor) {
        escribir("nodos." + nodo.id() + "." + clave, valor);
    }

    /** Resolvedor de ticket.* y usuario.*; lo pone el motor al cargar la ejecución. */
    private java.util.function.Function<String, Object> resolvedor;

    public void setResolvedor(java.util.function.Function<String, Object> resolvedor) {
        this.resolvedor = resolvedor;
    }

    public void escribir(String ruta, Object valor) {
        String[] partes = ruta.split("\\.");
        Map<String, Object> actual = variables;
        for (int i = 0; i < partes.length - 1; i++) {
            Object siguiente = actual.get(partes[i]);
            if (!(siguiente instanceof Map)) {
                // Un mapa nuevo en lugar de fallar: un flujo que sobrescribe un
                // objeto con un escalar casi siempre es un error de quien lo
                // construyó, y para eso está la validación del grafo.
                Map<String, Object> nuevo = new LinkedHashMap<>();
                actual.put(partes[i], nuevo);
                actual = nuevo;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> casteado = (Map<String, Object>) siguiente;
                actual = casteado;
            }
        }
        actual.put(partes[partes.length - 1], valor);
    }

    /** Copia de las variables, para guardarlas y releerlas después. */
    public Map<String, Object> variables() {
        return new LinkedHashMap<>(variables);
    }

    public void cargarVariables(Map<String, Object> guardadas) {
        variables.clear();
        if (guardadas != null) {
            variables.putAll(guardadas);
        }
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public int getDepth() {
        return depth;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public int getPasos() {
        return pasos;
    }

    public void sumarPaso() {
        pasos++;
    }

    /** Pasos ya registrados antes de esta vuelta (ejecución reanudada). */
    private int desplazamientoSecuencia;

    public int getDesplazamientoSecuencia() {
        return desplazamientoSecuencia;
    }

    public void setDesplazamientoSecuencia(int desplazamientoSecuencia) {
        this.desplazamientoSecuencia = desplazamientoSecuencia;
    }
}