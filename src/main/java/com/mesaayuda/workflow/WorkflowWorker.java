package com.mesaayuda.workflow;

import java.util.UUID;
import com.mesaayuda.config.WorkflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * El que recoge las ejecuciones pendientes y las ejecuta.
 *
 * <p><b>Nunca ejecuta nada dentro de la petición HTTP.</b> Un disparador encola y
 * este worker la recoge. La razón no es de estilo: una petición que espera a NEXA
 * puede tardar un minuto, y si eso colgara del hilo que atiende la creación de un
 * ticket, el help desk entero se pararía cada vez que una automatización usara la
 * IA.
 *
 * <p>El reparto usa {@code FOR UPDATE SKIP LOCKED}, y eso es lo que permite
 * levantar una segunda instancia sin configuring nada: las dos pueden preguntar a
 * la vez por trabajo pendiente y nunca toman la misma ejecución. Sin el, una
 * instancia esperaría a que la otra terminara su lote, y con trabajo lento se
 * perdería la mitad del rendimiento.
 *
 * <p>Además recoge las esperas vencidas y devuelve a la cola las ejecuciones
 * abandonadas: si el proceso muere a mitad, alguien tiene que rescatarlas o se
 * quedarían colgadas para siempre.
 */
@Component
public class WorkflowWorker {

    private static final Logger log = LoggerFactory.getLogger(WorkflowWorker.class);

    private final WorkflowColaService cola;
    private final WorkflowEngine engine;
    private final WorkflowTriggerService triggerService;
    private final WorkflowProperties properties;
    private final Executor executor;

    /** Evita que dos vueltas se pisen si una vuelta tarda más que el intervalo. */
    private final AtomicBoolean trabajando = new AtomicBoolean(false);

    /** Reloj de la última purga de mantenimiento. */
    private final AtomicLong ultimaLimpieza = new AtomicLong(0);

    public WorkflowWorker(WorkflowColaService cola,
                          WorkflowEngine engine,
                          WorkflowTriggerService triggerService,
                          WorkflowProperties properties,
                          @Qualifier("workflowExecutor") Executor executor) {
        this.cola = cola;
        this.engine = engine;
        this.triggerService = triggerService;
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * Una vuelta del worker.
     *
     * <p>Con {@code fixedDelay} y no {@code fixedRate}: si una vuelta se alarga,
     * la siguiente espera. Encadenar vueltas solapadas no acelera nada y sí
     * multiplica la carga sobre la base.
     */
    @Scheduled(fixedDelayString = "${app.workflow.intervalo-worker:1000}")
    public void recogerTrabajo() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!trabajando.compareAndSet(false, true)) {
            // La vuelta anterior sigue viva. Saltarse esta es preferible a
            // acumular trabajo: la cola no se va a perder, sigue en la base.
            log.debug("El worker se salta una vuelta porque la anterior sigue en curso.");
            return;
        }
        try {
            despertarEsperas();
            cola.cerrarAbandonadas();
            limpiar();
            ejecutarPendientes();
        } catch (RuntimeException e) {
            // Una vuelta que falla no debe matar el scheduler: sin esto, una
            // excepción puntual dejaría el motor apagado hasta reiniciar.
            log.error("La vuelta del worker de workflows falló: {}", e.getMessage(), e);
        } finally {
            trabajando.set(false);
        }
    }

    /** Esperas vencidas de vuelta a la cola; ver {@link WorkflowColaService#despertarVencidas()}. */
    private void despertarEsperas() {
        cola.despertarVencidas();
    }

    private void ejecutarPendientes() {
        int capacidad = capacidadDelPool();
        if (capacidad <= 0) {
            // El pool está saturado. No se encola nada nuevo: las ejecuciones
            // siguen en la base y se recogerán en una vuelta posterior. Perder
            // trabajo es imposible; perderlo sería un fallo de diseño.
            log.debug("El pool del motor está lleno ({}); esta vuelta no toma trabajo.", capacidad);
            return;
        }

        // Se toman y se marcan EJECUTANDO en la misma transacción; ver
        // WorkflowColaService.reclamarLote para el doble lanzamiento que esto evita.
        List<UUID> lote = cola.reclamarLote(capacidad);
        for (UUID id : lote) {
            executor.execute(() -> {
                try {
                    engine.ejecutar(id);
                } catch (RuntimeException e) {
                    // El motor ya guarda el fallo en la ejecución; aquí solo se
                    // deja constancia en el log para no perderlo.
                    log.error("La ejecución {} no se pudo procesar: {}", id, e.getMessage(), e);
                }
            });
        }
        if (!lote.isEmpty()) {
            log.debug("El worker tomó {} ejecuciones", lote.size());
        }
    }

    /**
     * Tareas de mantenimiento, una vez por vuelta pero con su propio reloj.
 *
 * <p>Purgar en cada vuelta sería trabajo inútil: se hace como mucho una vez por
     * minuto. Sin purgar, las claves de deduplicación y las ejecuciones
     * terminadas crecerían sin límite, porque nada las borra por su cuenta.
 */
    private void limpiar() {
        long ahora = System.currentTimeMillis();
        long anterior = ultimaLimpieza.get();
        if (ahora - anterior < 60_000L || !ultimaLimpieza.compareAndSet(anterior, ahora)) {
            return;
        }
        try {
            triggerService.limpiarDeduplicacion();
        } catch (RuntimeException e) {
            log.warn("No se pudo purgar la deduplicación de workflows: {}", e.getMessage());
        }
    }

    /** Cuántas ejecuciones caben ahora mismo en el pool. */
    private int capacidadDelPool() {
        if (executor instanceof ThreadPoolExecutor pool) {
            int libres = pool.getMaximumPoolSize() - pool.getActiveCount();
            return Math.min(libres, properties.getMaxEjecucionesConcurrentes());
        }
        return properties.getMaxEjecucionesConcurrentes();
    }
}