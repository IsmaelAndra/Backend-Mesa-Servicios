package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.repository.WorkflowWaitRepository;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Espera sin ocupar hilos.
 *
 * <p>La clave está en que <b>no duerme</b>. El motor guarda una fila en
 * {@code workflow_wait} con la hora de reanudación y termina la ejecución. Nadie
 * queda esperando: el proceso termina en milisegundos, y el hilo del worker
 * vuelve a estar libre.
 *
 * <p>La alternativa, un {@code Thread.sleep}, parecería más simple y sería un
 * desastre: con dos hilos de worker y cinco esperas de una hora, el motor se
 * quedaría sin nadie para atender nada más.
 */
@Component
public class WaitHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(WaitHandler.class);

    /** Tope de una sola espera: 30 días. */
    private static final int MINUTOS_MAXIMOS = 60 * 24 * 30;

    private final WorkflowWaitRepository waitRepository;

    public WaitHandler(WorkflowWaitRepository waitRepository) {
        this.waitRepository = waitRepository;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.WAIT;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        int minutos = numero(config.get("minutos"), 0);
        String hasta = contexto.configTexto(nodo, "hasta");
        OffsetDateTime reanudar;
        String tipoEspera;
        String descripcion;
        if (minutos > 0) {
            if (minutos > MINUTOS_MAXIMOS) {
                minutos = MINUTOS_MAXIMOS;
                log.warn("El nodo WAIT '{}' pedía más de {} minutos; se recorta. Una espera de "
                        + "meses casi siempre es un error en la configuración, no una intención.",
                        nodo.id(), MINUTOS_MAXIMOS);
            }
            reanudar = OffsetDateTime.now().plusMinutes(minutos);
            tipoEspera = WorkflowWait.TIPO_DELAY;
            descripcion = "Espera de " + minutos + " minutos hasta " + reanudar;
        } else if (hasta != null) {
            // Fecha fija o una variable con fecha, p. ej. {{ticket.sla.venceResolucion}}.
            try {
                reanudar = OffsetDateTime.parse(hasta);
            } catch (java.time.format.DateTimeParseException e) {
                return WorkflowResult.fallar("El nodo WAIT '" + nodo.id() + "' tiene una fecha 'hasta' "
                        + "que no es válida: '" + hasta + "'.");
            }
            if (!reanudar.isAfter(OffsetDateTime.now())) {
                // La fecha ya pasó: esperar sería no esperar.
                return WorkflowResult.avanzar();
            }
            if (reanudar.isAfter(OffsetDateTime.now().plusMinutes(MINUTOS_MAXIMOS))) {
                return WorkflowResult.fallar("El nodo WAIT '" + nodo.id() + "' espera hasta " + reanudar
                        + ", más de 30 días: casi siempre es un error de configuración.");
            }
            tipoEspera = WorkflowWait.TIPO_UNTIL;
            descripcion = "Espera hasta " + reanudar;
        } else {
            // Sin espera declarada, seguir pasa por el conector. Un WAIT vacío es
            // un nodo de más, y es mejor quitarlo del grafo.
            log.debug("El nodo WAIT '{}' no declara minutos ni fecha; se continúa.", nodo.id());
            return WorkflowResult.avanzar();
        }

        WorkflowWait espera = new WorkflowWait();
        espera.setExecutionExecution(contexto.getExecutionId());
        espera.setNodeIdWait(nodo.id());
        espera.setKindWait(tipoEspera);
        espera.setResumeAtWait(reanudar);
        espera.setStatusWait(WorkflowWait.ESTADO_PENDIENTE);
        espera.setCreatedAtWait(OffsetDateTime.now());
        waitRepository.save(espera);

        log.info("El nodo WAIT '{}' deja la ejecución {} en espera hasta {}",
                nodo.id(), contexto.getExecutionId(), reanudar);
        contexto.resultado(nodo, "reanudaEn", reanudar.toString());
        return WorkflowResult.parar(WorkflowExecution.ESTADO_ESPERANDO, descripcion);
    }

    private int numero(Object valor, int porDefecto) {
        if (valor == null) {
            return porDefecto;
        }
        if (valor instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(valor).trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}