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

/**
 * Dos salidas: SI o NO.
 *
 * <p>El nodo elige el puerto y el motor busca el conector. Esta separación es la
 * que permite que el validador compruebe que las dos ramas existen <i>antes</i> de
 * que el workflow se guarde, en lugar de descubrirlo en producción cuando un caso
 * que no se cumple se pierde en silencio.
 *
 * <p>La condición se guarda como {@code resultado} para que la traza diga por qué
 * se tomó esa rama. Sin eso, un proceso que hace algo inesperado no se puede
 * explicar sin volver a leer el grafo entero.
 */
@Component
public class IfHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(IfHandler.class);

    private static final String PUERTO_SI = "SI";
    private static final String PUERTO_NO = "NO";

    private final WorkflowConditionEvaluator conditions;

    public IfHandler(WorkflowConditionEvaluator conditions) {
        this.conditions = conditions;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.IF;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        boolean cumple = conditions.evaluar(contexto, nodo);
        String puerto = cumple ? PUERTO_SI : PUERTO_NO;
        log.debug("El nodo IF '{}' evaluó {}: se va por {}", nodo.id(), cumple ? "verdadero" : "falso", puerto);

        contexto.escribir("ultimoSi", cumple);
        contexto.escribir("ultimoNodoCondicion", nodo.id());
        return WorkflowResult.avanzarPor(puerto);
    }
}