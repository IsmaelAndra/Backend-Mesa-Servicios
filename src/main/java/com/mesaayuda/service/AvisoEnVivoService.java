package com.mesaayuda.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Avisos en vivo "algo tuyo cambió, vuelve a cargar" (SSE, evento {@code cambio}):
 * actividades, actas, dispositivos con sesión. Solo dicen QUÉ cambió (tipo e id),
 * nunca el contenido: la pantalla lo vuelve a pedir por la API con sus permisos.
 *
 * <p>Se manda después del commit: si llegara antes, la pantalla recargaría y aún
 * no vería el cambio.
 */
@Service
public class AvisoEnVivoService {

    private static final Logger log = LoggerFactory.getLogger(AvisoEnVivoService.class);

    private final SseEventBroker broker;

    public AvisoEnVivoService(SseEventBroker broker) {
        this.broker = broker;
    }

    /** @param tipo "actividad", "acta", "sesiones"... */
    public void avisar(Collection<UUID> usuarios, String tipo, UUID id) {
        Set<UUID> destino = usuarios == null ? Set.of()
                : usuarios.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (destino.isEmpty()) {
            return;
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", tipo);
        if (id != null) {
            datos.put("id", id.toString());
        }
        Runnable enviar = () -> {
            try {
                broker.publishAUsuarios(destino, "cambio", datos);
            } catch (RuntimeException e) {
                log.debug("No se pudo mandar el aviso en vivo {}", tipo, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar.run();
                }
            });
        } else {
            enviar.run();
        }
    }
}
