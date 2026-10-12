package com.mesaayuda.workflow;

import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.SseEventBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emite por SSE los pasos de una ejecución mientras ocurren, para que el lienzo
 * del constructor y el historial la animen en vivo.
 *
 * <p>Es solo una ventana: lo que manda ya está guardado en {@code workflow_node_run}.
 * Si nadie está conectado no se hace nada, y un fallo al enviar nunca afecta a la
 * ejecución. La repetición del historial sale de la base, no de aquí.
 *
 * <p>El destinatario es quien la lanzó (el autor si la disparó un evento), y solo
 * si es super administrador activo: la gestión de workflows es exclusiva de ese
 * rol, y el paso lleva la salida del nodo.
 */
@Component
public class WorkflowEnVivo {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEnVivo.class);

    /** Cuánto se fía del rol consultado antes de volver a mirarlo. */
    private static final long VIGENCIA_ROL_MS = 60_000;

    public static final String NODO_INICIO = "NODO_INICIO";
    public static final String NODO_FIN = "NODO_FIN";
    public static final String FIN = "FIN";

    private final SseEventBroker broker;
    private final UserRepository userRepository;

    /** usuario → (es super admin, cuándo se comprobó). Evita una consulta por nodo. */
    private final Map<UUID, long[]> rolesVistos = new ConcurrentHashMap<>();

    public WorkflowEnVivo(SseEventBroker broker, UserRepository userRepository) {
        this.broker = broker;
        this.userRepository = userRepository;
    }

    /**
     * Un paso de la ejecución.
     *
     * @param tipo      NODO_INICIO, NODO_FIN o FIN
     * @param estado    EJECUTANDO / COMPLETADO / ERROR en los nodos; el estado de la
     *                  ejecución (COMPLETADO, ERROR, ESPERANDO...) en FIN
     * @param puerto    salida elegida por el nodo (rama tomada)
     * @param siguiente nodo al que sigue, si continúa
     * @param salida    lo que produjo el nodo (variables nuevas o cambiadas)
     */
    public record Paso(UUID executionId, UUID workflowId, String tipo, String nodeId, String nodeType,
                       Integer secuencia, String estado, String puerto, String siguiente, String motivo,
                       Map<String, Object> salida, Long duracionMs, Instant at) {
    }

    public void emitir(UUID destinatario, Paso paso) {
        try {
            if (destinatario == null || !broker.isConectado(destinatario) || !esSuperAdmin(destinatario)) {
                return;
            }
            broker.publishWorkflow(destinatario, paso);
        } catch (RuntimeException e) {
            // La animación es accesoria: jamás debe tumbar una ejecución.
            log.debug("No se pudo emitir el paso en vivo de la ejecución {}: {}", paso.executionId(), e.getMessage());
        }
    }

    private boolean esSuperAdmin(UUID userId) {
        long ahora = System.currentTimeMillis();
        long[] visto = rolesVistos.get(userId);
        if (visto != null && ahora - visto[1] < VIGENCIA_ROL_MS) {
            return visto[0] == 1;
        }
        User u = userRepository.findById(userId).orElse(null);
        boolean es = u != null
                && !Boolean.TRUE.equals(u.getDeletedUser())
                && "ACTIVE".equalsIgnoreCase(u.getStatusUser())
                && u.getRolUser() != null
                && "SUPER_ADMIN".equalsIgnoreCase(u.getRolUser().getNameRole());
        rolesVistos.put(userId, new long[]{es ? 1 : 0, ahora});
        return es;
    }
}
