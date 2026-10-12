package com.mesaayuda.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mesaayuda.service.MicrosoftGraphUserSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincronización periódica de usuarios desde Microsoft Graph.
 *
 * <p>La frecuencia efectiva se controla desde la configuración de Microsoft Graph
 * ({@code sync_interval_minutes}); este scheduler solo "despierta" cada pocos
 * minutos y {@link MicrosoftGraphUserSyncService#syncAll()} decide si ya venció
 * el intervalo configurado para cada usuario.</p>
 */
@Component
public class UserSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncScheduler.class);

    @Autowired
    private MicrosoftGraphUserSyncService userSyncService;

    @Scheduled(fixedDelayString = "${app.scheduler.graph-sync.fixed-delay:600000}")
    @SchedulerLock(name = "sincronizacionGraph", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void syncMicrosoftUsers() {
        try {
            int updated = userSyncService.syncAll();
            if (updated > 0) {
                log.info("Sincronizados {} usuarios con Microsoft Graph", updated);
            }
        } catch (Exception e) {
            log.warn("Fallo la sincronización programada con Microsoft Graph: {}", e.getMessage());
        }
    }
}