package com.mesaayuda.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mesaayuda.service.DailySummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailySummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryScheduler.class);

    @Autowired
    private DailySummaryService dailySummaryService;

    /** Envía el resumen diario de la operación (por defecto, todos los días a las 08:00). */
    @Scheduled(cron = "${app.scheduler.daily-summary.cron:0 0 8 * * *}", zone = "America/Guayaquil")
    @SchedulerLock(name = "resumenDiario", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void sendDailySummary() {
        try {
            var result = dailySummaryService.send();
            log.info("Resumen diario programado: {} correo(s) a {}", result.sent(), result.recipients());
        } catch (Exception e) {
            log.error("Error enviando el resumen diario", e);
        }
    }
}