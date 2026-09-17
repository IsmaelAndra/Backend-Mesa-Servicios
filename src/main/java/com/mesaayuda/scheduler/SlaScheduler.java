package com.mesaayuda.scheduler;

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
    public void checkSla() {
        int violations = slaService.checkSlaViolations().size();
        if (violations > 0) {
            log.warn("Detectadas {} violaciones de SLA", violations);
        }
        slaService.checkSlaWarnings();
    }

    /** Procesa notificaciones pendientes cada 5 minutos. */
    @Scheduled(cron = "${app.scheduler.notifications.cron:0 */5 * * * *}")
    public void retryPendingNotifications() {
        notificationService.processPendingNotifications();
    }
}