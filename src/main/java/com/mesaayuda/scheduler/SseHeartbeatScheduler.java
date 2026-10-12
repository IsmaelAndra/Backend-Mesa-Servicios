package com.mesaayuda.scheduler;

import com.mesaayuda.service.SseEventBroker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene vivas las conexiones SSE.
 *
 * Sin esto, una conexion que se cae en silencio (se apaga el equipo, se corta la
 * red, el movil pierde senal) no provoca ningun error en el servidor: sigue en
 * el mapa de clientes y el usuario queda marcado como "en linea" para siempre.
 * El latido hace que el servidor intente escribir y, al fallar, lo limpie.
 *
 * Ademas mantiene vivas las conexiones a traves de proxies que cortan las
 * conexiones SSE sin actividad.
 */
@Component
public class SseHeartbeatScheduler {

    private final SseEventBroker sseEventBroker;

    public SseHeartbeatScheduler(SseEventBroker sseEventBroker) {
        this.sseEventBroker = sseEventBroker;
    }

    @Scheduled(fixedDelay = 25_000L, initialDelay = 25_000L)
    public void enviarLatido() {
        sseEventBroker.latido();
    }
}
