package com.mesaayuda.workflow.handler;

import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

/**
 * Arranque del grafo.
 *
 * <p>No hace nada. Existe porque un grafo necesita un punto de entrada conocido, y
 * porque tener un nodo explícito deja sitio para lo que se quiera ejecutar al
 * empezar sin tener que cambiar el motor.
 */
@Component
public class InicioHandler implements WorkflowNodeHandler {

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.INICIO;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        return WorkflowResult.avanzar();
    }
}