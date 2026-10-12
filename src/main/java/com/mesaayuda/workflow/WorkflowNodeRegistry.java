package com.mesaayuda.workflow;

import com.mesaayuda.workflow.handler.ApprovalHandler;
import com.mesaayuda.workflow.handler.AuditHandler;
import com.mesaayuda.workflow.handler.EmailHandler;
import com.mesaayuda.workflow.handler.FinHandler;
import com.mesaayuda.workflow.handler.IfHandler;
import com.mesaayuda.workflow.handler.InicioHandler;
import com.mesaayuda.workflow.handler.NexaHandler;
import com.mesaayuda.workflow.handler.NotifyHandler;
import com.mesaayuda.workflow.handler.SlaApplyHandler;
import com.mesaayuda.workflow.handler.SwitchHandler;
import com.mesaayuda.workflow.handler.TaskCreateHandler;
import com.mesaayuda.workflow.handler.TicketAssignHandler;
import com.mesaayuda.workflow.handler.TicketCategoryHandler;
import com.mesaayuda.workflow.handler.TicketCloseHandler;
import com.mesaayuda.workflow.handler.TicketCommentHandler;
import com.mesaayuda.workflow.handler.TicketPriorityHandler;
import com.mesaayuda.workflow.handler.TicketReopenHandler;
import com.mesaayuda.workflow.handler.TicketStatusHandler;
import com.mesaayuda.workflow.handler.WaitHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Los tipos de nodo que el motor sabe ejecutar, y la comprobación de que esa lista
 * está completa.
 *
 * <p>El registro se construye una vez al arrancar y se valida contra
 * {@link WorkflowNodeType}. Un tipo del catálogo sin handler es un fallo de
 * arranque, no un error en caliente: es código incompleto, y se quiere saber al
 * levantar la aplicación y no cuando alguien publique un workflow que lo use.
 */
@Component
public class WorkflowNodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNodeRegistry.class);

    private final Map<WorkflowNodeType, WorkflowNodeHandler> handlers = new EnumMap<>(WorkflowNodeType.class);

    public WorkflowNodeRegistry(List<WorkflowNodeHandler> encontrados) {
        for (WorkflowNodeHandler h : encontrados) {
            WorkflowNodeHandler previo = handlers.put(h.tipo(), h);
            if (previo != null) {
                // Dos beans del mismo tipo: cuál de los dos gana dependería del
                // orden de inyección, y eso es una forma silenciosa de que un
                // cambio de comportamiento aparezca sin tocar nada.
                throw new IllegalStateException("Hay dos handlers para el tipo de nodo "
                        + h.tipo() + ": " + previo.getClass().getSimpleName() + " y "
                        + h.getClass().getSimpleName());
            }
        }
        exigirCoberturaCompleta();
        log.info("Motor de workflows: {} tipos de nodo disponibles", handlers.size());
    }

    /**
     * Handler de un tipo, o {@code null} si no lo hay.
     *
     * <p>El grafo ya se validó al publicarse, así que llegar aquí sin handler
     * significa que el grafo se guardó con un tipo que el catálogo ya no reconoce.
     * Puede pasar si alguien quita un tipo del enum sin mirar lo que hay en la
     * base, y por eso el motor lo trata como error de ejecución y no como
     * "sáltame este nodo".
     */
    public WorkflowNodeHandler handler(WorkflowNodeType tipo) {
        return handlers.get(tipo);
    }

    public boolean soporta(WorkflowNodeType tipo) {
        return handlers.containsKey(tipo);
    }

    /**
     * Todo tipo del catálogo tiene que tener handler.
     *
     * <p>La lista cerrada es lo que impide que un workflow ejecute nada
     * arbitrario. Si el catálogo crece y nadie añade el handler, el motor
     * arrancaría igual y el fallo aparecería weeks después, con el workflow
     * publicado desde hace meses y sin forma clara de por qué se paró.
     */
    private void exigirCoberturaCompleta() {
        Set<WorkflowNodeType> sinHandler = new HashSet<>(Set.of(WorkflowNodeType.values()));
        sinHandler.removeAll(handlers.keySet());
        if (!sinHandler.isEmpty()) {
            throw new IllegalStateException("Tipos de nodo sin handler en el motor: " + sinHandler
                    + ". Añade el handler o quita el tipo del catálogo. Un tipo sin handler "
                    + "es un workflow que se publicará y luego fallará al ejecutarse.");
        }
    }
}