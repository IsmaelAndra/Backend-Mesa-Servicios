package com.mesaayuda.workflow.handler;

import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

/**
 * Salida del grafo.
 *
 * <p>Es el único nodo que termina la ejecución por diseño. Varios FIN pueden
 * convivir en un mismo grafo: cada rama termina en el suyo, y es más claro que
 * un IF que decide si "termina".
 */
@Component
public class FinHandler implements WorkflowNodeHandler {

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.FIN;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        return WorkflowResult.parar(
                com.mesaayuda.entity.WorkflowExecution.ESTADO_COMPLETADO,
                "Fin del flujo.");
    }
}