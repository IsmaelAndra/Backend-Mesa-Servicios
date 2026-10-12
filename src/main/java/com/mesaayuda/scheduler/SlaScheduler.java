package com.mesaayuda.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mesaayuda.service.NotificationService;
import com.mesaayuda.service.SlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    @Autowired
    private SlaService slaService;

    @Autowired
    private NotificationService notificationService;

    /** Verifica vencimientos SLA cada 15 minutos (días hábiles). */
    @Scheduled(cron = "${app.scheduler.sla.cron:0 */15 * * * MON-FRI}")
    @SchedulerLock(name = "revisionSla", lockAtMostFor = "PT14M", lockAtLeastFor = "PT30S")
    public void checkSla() {
        int violations = slaService.checkSlaViolations().size();
        if (violations > 0) {
            log.warn("Detectadas {} violaciones de SLA", violations);
        }
        slaService.checkSlaWarnings();
    }

    // El antiguo "reintento de notificaciones" cada 5 minutos se retiró: volvía
    // a enviar por correo todas las notificaciones PENDING, que nunca se
    // marcaban como enviadas, así que cada aviso llegaba dos veces. Ahora el
    // envío y sus reintentos los lleva la cola de correo (ColaCorreoScheduler).
}