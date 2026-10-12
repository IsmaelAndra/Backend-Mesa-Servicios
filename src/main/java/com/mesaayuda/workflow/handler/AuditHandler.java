package com.mesaayuda.workflow.handler;

import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Deja un asiento en la auditoría del sistema.
 *
 * <p>Existe para que un proceso deje constancia de por qué hizo algo. Es
 * especialmente útil después de un cambio automático que nadie ha pedido en el
 * momento: el asiento dice qué proceso lo hizo, cuándo y con qué variables.
 *
 * <p>Se escribe con la auditoría del sistema, que va en su propia transacción.
 * Si compartiera la transacción de la ejecución y esta fallara después, el
 * asiento se perdería junto con la explicación.
 */
@Component
public class AuditHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditHandler.class);

    private final SystemAuditService systemAuditService;

    public AuditHandler(SystemAuditService systemAuditService) {
        this.systemAuditService = systemAuditService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.AUDIT;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        String accion = config.get("accion") == null
                ? "WORKFLOW_STEP" : String.valueOf(config.get("accion")).trim().toUpperCase(java.util.Locale.ROOT);
        // La acción siempre empieza por WORKFLOW_. Sin esto, un workflow podría
        // escribir "LOGIN" o "USER_DELETE" y dejar en la auditoría un asiento
        // indistinguible de uno real; y la detección de actividad sospechosa lee
        // esas mismas acciones.
        accion = accion.replaceAll("[^A-Z0-9_]", "_");
        if (!accion.startsWith("WORKFLOW_")) {
            accion = "WORKFLOW_" + accion;
        }
        if (accion.length() > 50) {
            accion = accion.substring(0, 50);
        }

        String configurado = contexto.configTexto(nodo, "detalle");
        Object detalle = configurado == null
                ? "Paso '" + nodo.id() + "' del workflow " + contexto.getWorkflowId()
                : configurado;

        systemAuditService.record(accion, "Workflow", java.util.UUID.fromString(contexto.getWorkflowId().toString()),
                "workflow:" + contexto.getWorkflowId(), String.valueOf(detalle));

        log.debug("El nodo AUDIT '{}' dejó el asiento '{}'", nodo.id(), accion);
        return WorkflowResult.avanzar();
    }
}