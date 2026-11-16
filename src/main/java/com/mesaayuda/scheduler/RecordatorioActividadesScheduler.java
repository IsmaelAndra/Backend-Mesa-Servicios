package com.mesaayuda.scheduler;

import com.mesaayuda.service.TaskService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Cada 5 minutos avisa de las actividades cuyo plazo se acerca (Fase 3). */
@Component
public class RecordatorioActividadesScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioActividadesScheduler.class);

    private final TaskService taskService;

    public RecordatorioActividadesScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.recordatorio-actividades-ms:300000}", initialDelay = 60000)
    @SchedulerLock(name = "recordatorioActividades", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void avisar() {
        try {
            int n = taskService.enviarRecordatoriosPendientes(OffsetDateTime.now());
            if (n > 0) {
                log.info("Recordatorios de actividades enviados: {}", n);
            }
        } catch (Exception e) {
            log.error("Error enviando recordatorios de actividades", e);
        }
    }
}
