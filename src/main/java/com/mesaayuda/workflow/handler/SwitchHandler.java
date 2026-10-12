package com.mesaayuda.workflow.handler;

import com.mesaayuda.workflow.WorkflowConditionEvaluator;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Una salida por cada valor posible.
 *
 * <p>A diferencia del IF, aquí los casos los declara quien construye el workflow,
 * en la configuración del nodo:
 *
 * <pre>
 * {"casos":[{"valor":"CRITICA","salida":"critica","etiqueta":"critica"},
 *           {"valor":"ALTA","salida":"alta","etiqueta":"alta"}]}
 * </pre>
 *
 * <p>La comparación es exacta y sin distinguir mayúsculas, porque los valores
 * vienen de catálogos que el usuario escribe a mano y "Critica" y "CRITICA" no
 * deberían ser dos ramas distintas.
 *
 * <p>Si ningún caso coincide se va a {@code por_defecto}, y si tampoco existe, la
 * ejecución para con error. Un SWITCH que cae en un hueco sin avisar dejaría el
 * proceso parado sin motivo aparente.
 */
@Component
public class SwitchHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(SwitchHandler.class);
    private static final String PUERTO_DEFECTO = "por_defecto";

    private final WorkflowConditionEvaluator conditions;

    public SwitchHandler(WorkflowConditionEvaluator conditions) {
        this.conditions = conditions;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.SWITCH;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        Object brutoCasos = config.get("casos");
        if (!(brutoCasos instanceof List<?> casos) || casos.isEmpty()) {
            return WorkflowResult.fallar("El SWITCH '" + nodo.id() + "' no define sus casos.");
        }

        String campo = config.get("campo") == null ? null : String.valueOf(config.get("campo"));
        if (campo == null || campo.isBlank()) {
            return WorkflowResult.fallar("El SWITCH '" + nodo.id() + "' no dice qué campo compara.");
        }

        Object valor = contexto.leer(campo);

        for (Object elemento : casos) {
            if (!(elemento instanceof Map<?, ?> caso)) {
                continue;
            }
            Object valorCaso = caso.get("valor");
            if (valorCaso == null || !coincide(valor, valorCaso)) {
                continue;
            }
            Object salida = caso.get("salida");
            if (salida == null) {
                return WorkflowResult.fallar("El caso '" + valorCaso + "' del SWITCH '"
                        + nodo.id() + "' no dice por qué puerto sale.");
            }
            log.debug("El SWITCH '{}' coincide '{}' con '{}' y sale por '{}'",
                    nodo.id(), campo, valorCaso, salida);
            contexto.escribir("ultimoSwitch", valorCaso);
            return WorkflowResult.avanzarPor(String.valueOf(salida));
        }

        Object defecto = config.get("por_defecto");
        if (defecto != null && !String.valueOf(defecto).isBlank()) {
            log.debug("El SWITCH '{}' no encontró caso para '{}' y va por defecto '{}'",
                    nodo.id(), valor, defecto);
            return WorkflowResult.avanzarPor(String.valueOf(defecto));
        }

        return WorkflowResult.fallar("Ningún caso del SWITCH '" + nodo.id() + "' coincide con '"
                + valor + "' y no hay salida por defecto. Añade el caso que falta o un "
                + "'por_defecto' en la configuración.");
    }

    private boolean coincide(Object valor, Object valorCaso) {
        if (valor == null) {
            return false;
        }
        return String.valueOf(valor).trim().equalsIgnoreCase(String.valueOf(valorCaso).trim());
    }
}