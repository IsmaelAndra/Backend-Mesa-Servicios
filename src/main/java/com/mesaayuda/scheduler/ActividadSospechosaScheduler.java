package com.mesaayuda.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mesaayuda.service.ActividadSospechosaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Revisa audit_log cada cinco minutos en busca de actividad sospechosa. */
@Component
public class ActividadSospechosaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActividadSospechosaScheduler.class);

    @Autowired
    private ActividadSospechosaService actividadSospechosaService;

    @Scheduled(cron = "${app.scheduler.suspicious.cron:0 */5 * * * *}", zone = "America/Guayaquil")
    @SchedulerLock(name = "actividadSospechosa", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void revisar() {
        try {
            actividadSospechosaService.revisar();
        } catch (Exception e) {
            log.error("Fallo al revisar la actividad sospechosa: {}", e.getMessage(), e);
        }
    }
}
