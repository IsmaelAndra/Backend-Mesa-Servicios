package com.mesaayuda.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Evalúa la condición de un nodo IF.
 *
 * <p><b>Aquí no se interpreta nada.</b> No hay {@code eval}, ni motor de expresiones,
 * ni compilación de texto. Solo un conjunto fijo de operadores contra una ruta de
 * variables. Un motor de automatizaciones que evaluara texto sería, en el mejor de
 * los casos, una superficie de inyección y una fuente de errores imposible de
 * reproducir; el catálogo de operadores lo hace predecible y auditable.
 *
 * <p>Las comparaciones numéricas y de fecha van por {@link BigDecimal} para que
 * "10" > "9" no dependa de si llegaron como texto o como número. Un workflow no
 * debería comportarse distinto según de dónde salió el dato.
 */
@Component
public class WorkflowConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConditionEvaluator.class);

    private static final Set<String> OPERADORES = Set.of(
            "==", "!=", ">", "<", ">=", "<=",
            "contiene", "empieza_con", "termina_con",
            "vacio", "no_vacio", "es_verdadero", "es_falso");

    /**
     * Decide si la condición se cumple.
     *
     * <p>Una condición mal escrita da {@code false} y deja una nota en el log, en
     * lugar de romper la ejecución. La razón: un proceso no debería caerse entero
     * por un campo mal escrito; debe seguir por la rama que sea y quedarse
     * registrado que algo no cuadra.
     */
    public boolean evaluar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        String campo = texto(config.get("campo"));
        String operador = texto(config.get("operador"));
        Object esperado = config.get("valor");
        // El valor esperado también puede ser una variable: "prioridad del ticket
        // == {{nodos.nexa1.prioridad}}".
        if (esperado instanceof String texto) {
            esperado = contexto.interpolar(texto);
        }

        if (campo == null || operador == null) {
            log.warn("El nodo IF '{}' no dice qué comprueba (falta campo u operador). Se toma como falso.",
                    nodo.id());
            return false;
        }
        if (!OPERADORES.contains(operador)) {
            // Con un operador desconocido no se adivina. Adivinar sería peor que
            // fallar: el proceso seguiría por una rama que nadie eligió.
            log.warn("El nodo IF '{}' usa el operador desconocido '{}'. Se toma como falso. Operadores: {}",
                    nodo.id(), operador, OPERADORES);
            return false;
        }

        Object actual = contexto.leer(campo);
        try {
            return comparar(actual, operador, esperado);
        } catch (RuntimeException e) {
            log.warn("No se pudo comparar '{}' con el operador '{}' en el nodo '{}': {}",
                    campo, operador, nodo.id(), e.getMessage());
            return false;
        }
    }

    private boolean comparar(Object actual, String operador, Object esperado) {
        switch (operador) {
            case "vacio":
                return actual == null || String.valueOf(actual).isBlank();
            case "no_vacio":
                return actual != null && !String.valueOf(actual).isBlank();
            case "es_verdadero":
                return esVerdad(actual);
            case "es_falso":
                return !esVerdad(actual);
            case "contiene":
                return actual != null && String.valueOf(actual).contains(String.valueOf(esperado));
            case "empieza_con":
                return actual != null && String.valueOf(actual).startsWith(String.valueOf(esperado));
            case "termina_con":
                return actual != null && String.valueOf(actual).endsWith(String.valueOf(esperado));
            case "==":
                return iguales(actual, esperado);
            case "!=":
                return !iguales(actual, esperado);
            default:
                return compararNumerico(actual, operador, esperado);
        }
    }

    private boolean esVerdad(Object valor) {
        if (valor == null) {
            return false;
        }
        if (valor instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(valor).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "si".equalsIgnoreCase(s)
                || "sí".equalsIgnoreCase(s) || "verdadero".equalsIgnoreCase(s);
    }

    private boolean iguales(Object actual, Object esperado) {
        if (actual == null || esperado == null) {
            return actual == null && esperado == null;
        }
        try {
            return decimal(actual).compareTo(decimal(esperado)) == 0;
        } catch (NumberFormatException e) {
            return String.valueOf(actual).equalsIgnoreCase(String.valueOf(esperado));
        }
    }

    private boolean compararNumerico(Object actual, String operador, Object esperado) {
        if (actual == null || esperado == null) {
            // Comparar con null en una desigualdad no significa nada. Se devuelve
            // false para que la rama alternativa sea la que se ejecute.
            return false;
        }
        int cmp = decimal(actual).compareTo(decimal(esperado));
        return switch (operador) {
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    private BigDecimal decimal(Object valor) {
        if (valor instanceof BigDecimal d) {
            return d;
        }
        if (valor instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (valor instanceof OffsetDateTime fecha) {
            return BigDecimal.valueOf(fecha.toEpochSecond());
        }
        if (valor instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(valor).trim());
    }

    private String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = String.valueOf(valor).trim();
        return s.isEmpty() ? null : s;
    }
}