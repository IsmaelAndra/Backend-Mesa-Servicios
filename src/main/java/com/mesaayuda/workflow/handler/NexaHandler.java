package com.mesaayuda.workflow.handler;

import com.mesaayuda.service.AiAssistantService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * NEXA, la IA de la mesa, como un paso del workflow.
 *
 * <p>No es otra IA: delega en {@link AiAssistantService}, con el proveedor, la
 * configuración y las reglas de la institución que ya tiene. El nodo solo elige
 * qué se le pide y deja el resultado donde los nodos siguientes lo pueden leer.
 *
 * <p>Acciones ({@code accion} en la configuración):
 * <ul>
 *   <li><b>CLASIFICAR</b>: deja en {@code nodos.<id>.*} prioridad, prioridadId,
 *       categoria, categoriaId, equipo, equipoId, resumen y confianza, todos
 *       contrastados con los catálogos. Es lo que permite "si NEXA dice CRÍTICA...".</li>
 *   <li><b>RESUMIR</b> / <b>RESPONDER</b>: texto en {@code nodos.<id>.texto}.</li>
 * </ul>
 * Se aceptan también los nombres de la API de IA (CLASSIFY, SUMMARY, ANSWER).
 */
@Component
public class NexaHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(NexaHandler.class);

    private static final int MAX_CARACTERES = 20_000;

    private final AiAssistantService aiAssistantService;

    public NexaHandler(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.NEXA;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        String accion = accion(contexto.configTexto(nodo, "accion"));
        if (accion == null) {
            return WorkflowResult.fallar("El nodo NEXA '" + nodo.id() + "' pide una acción que NEXA no tiene. "
                    + "Disponibles: CLASIFICAR, RESUMIR, RESPONDER.");
        }
        UUID ticketId = idTicket(contexto, nodo);
        if (ticketId == null) {
            return WorkflowResult.fallar("El nodo NEXA '" + nodo.id()
                    + "' necesita un ticket: NEXA trabaja sobre un ticket concreto.");
        }
        String instrucciones = contexto.configTexto(nodo, "instrucciones");
        try {
            if (AiAssistantService.ACCION_CLASIFICAR.equals(accion)) {
                Map<String, Object> clasificacion = aiAssistantService.clasificar(ticketId, instrucciones);
                clasificacion.forEach((clave, valor) -> contexto.resultado(nodo, clave, valor));
                log.info("NEXA clasificó el ticket #{} en el nodo '{}': {}", ticketId, nodo.id(), clasificacion);
                return WorkflowResult.avanzar();
            }
            String respuesta = aiAssistantService.assist(accion, ticketId,
                    instrucciones == null ? "Resume el ticket de forma útil para el equipo de soporte." : instrucciones);
            if (respuesta == null || respuesta.isBlank()) {
                return WorkflowResult.fallar("NEXA devolvió una respuesta vacía en el nodo '" + nodo.id() + "'.");
            }
            String recortada = respuesta.length() <= MAX_CARACTERES ? respuesta : respuesta.substring(0, MAX_CARACTERES);
            contexto.resultado(nodo, "texto", recortada);
            // Compatibilidad con grafos anteriores, que leían la variable plana.
            String variable = contexto.configTexto(nodo, "variable");
            contexto.escribir(variable == null ? "nexaResultado" : variable, recortada);
            return WorkflowResult.avanzar();
        } catch (com.mesaayuda.exception.AiException e) {
            log.warn("NEXA falló en el nodo '{}' para el ticket #{}: {}", nodo.id(), ticketId, e.getMessage());
            String motivo = "NEXA no pudo completar la acción en el nodo '" + nodo.id() + "': " + e.getMessage();
            // Saturación o timeout del proveedor: pasajero, se reintenta. Con la IA
            // apagada en la configuración, reintentar no arregla nada.
            boolean pasajero = e.getMessage() == null || !e.getMessage().toLowerCase().contains("deshabilitad");
            return pasajero ? WorkflowResult.fallarReintentable(motivo) : WorkflowResult.fallar(motivo);
        } catch (RuntimeException e) {
            log.warn("NEXA falló en el nodo '{}' para el ticket #{}: {}", nodo.id(), ticketId, e.getMessage());
            return WorkflowResult.fallar("NEXA no pudo completar la acción en el nodo '" + nodo.id() + "': "
                    + e.getMessage());
        }
    }

    /** Nombre de la acción para AiAssistantService, o null si no existe. */
    public static String accion(String configurada) {
        String a = configurada == null ? "CLASIFICAR" : configurada.trim().toUpperCase(Locale.ROOT);
        return switch (a) {
            case "CLASIFICAR", "CLASSIFY" -> AiAssistantService.ACCION_CLASIFICAR;
            case "RESUMIR", "SUMMARY" -> "SUMMARY";
            case "RESPONDER", "ANSWER" -> "ANSWER";
            default -> null;
        };
    }

    private UUID idTicket(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        Object[] candidatos = {config.get("ticketId"), contexto.leer("ticketId"),
                contexto.leer("ticket.id"), contexto.leer("idTicket")};
        for (Object c : candidatos) {
            if (c == null) {
                continue;
            }
            if (c instanceof java.util.UUID u) {
                return u;
            }
            try {
                return UUID.fromString(String.valueOf(c).trim());
            } catch (IllegalArgumentException e) {
                // Se prueba con el siguiente.
            }
        }
        return null;
    }
}
