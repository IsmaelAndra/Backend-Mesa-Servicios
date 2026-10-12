package com.mesaayuda.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.mesaayuda.service.EvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvaluationScheduler.class);

    @Autowired
    private EvaluationService evaluationService;

    /** Genera la evaluación mensual de desempeño el día 1 de cada mes a las 06:00. */
    @Scheduled(cron = "${app.scheduler.evaluation.cron:0 0 6 1 * *}")
    @SchedulerLock(name = "evaluacionMensual", lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    public void generateMonthlyEvaluations() {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        log.info("Generando evaluaciones mensuales para {}", month);
        int generated = evaluationService.generateAllForMonth(month).size();
        log.info("Evaluaciones generadas: {}", generated);
    }
}