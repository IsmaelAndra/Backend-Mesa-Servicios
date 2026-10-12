package com.mesaayuda.workflow.handler;

import com.mesaayuda.dto.TaskRequest;
import com.mesaayuda.service.TaskService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Crea una tarea.
 *
 * <p>Delega en {@code TaskService.create}, que ya avisa a quien se la asigna.
 */
@Component
public class TaskCreateHandler implements WorkflowNodeHandler {

    private final TaskService taskService;

    public TaskCreateHandler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.TASK_CREATE;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();

        String titulo = contexto.configTexto(nodo, "titulo");
        if (titulo == null || titulo.isEmpty()) {
            return WorkflowResult.fallar("El nodo TASK_CREATE '" + nodo.id() + "' no tiene título.");
        }

        String estado = config.get("estado") == null ? "PENDIENTE" : String.valueOf(config.get("estado"));
        java.util.UUID asignadoCfg = contexto.configUuid(nodo, "asignadoId");
        java.util.UUID asignado = null;
        if (asignadoCfg != null) {
            asignado = asignadoCfg;
        }
        if (asignado == null) {
            asignado = uuid(contexto.leer("ticket.agenteId"), contexto.leer("agenteId"));
        }

        var tarea = taskService.create(new TaskRequest(titulo,
                contexto.configTexto(nodo, "descripcion"),
                estado, asignado, null, null,
                entero(config.get("duracionMinutos")),
                config.get("color") == null ? null : String.valueOf(config.get("color"))));

        // No hace falta registrar aquí que pasó: el rastro de la ejecución ya
        // guarda un paso por nodo. Una segunda copia del mismo dato en el log solo
        // añade ruido.
        contexto.resultado(nodo, "tareaId", tarea == null ? null : tarea.id());
        return WorkflowResult.avanzar();
    }

    private Integer entero(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(valor).trim());
        } catch (NumberFormatException e) {
            // Duración no legible: se deja vacía en vez de tumbar la ejecución. Una
            // tarea sin duración estimada sigue siendo útil.
            return null;
        }
    }

    private java.util.UUID uuid(Object valor, Object alternativa) {
        Object[] candidatos = {valor, alternativa};
        for (Object c : candidatos) {
            if (c == null) {
                continue;
            }
            if (c instanceof java.util.UUID u) {
                return u;
            }
            try {
                return java.util.UUID.fromString(String.valueOf(c).trim());
            } catch (Exception e) {
                // Siguiente candidato.
            }
        }
        return null;
    }
}