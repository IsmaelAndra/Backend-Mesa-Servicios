package com.mesaayuda.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Bloqueo de tareas programadas entre instancias (ShedLock).
 *
 * <p>Con dos servidores levantados, cada {@code @Scheduled} se ejecutaba en los
 * dos: el SLA avisaba dos veces, la evaluación mensual se generaba doble y la
 * sincronización con Graph competía consigo misma. Las tareas marcadas con
 * {@code @SchedulerLock} toman una fila de la tabla {@code shedlock} y solo la
 * instancia que la consigue trabaja.
 *
 * <p>No llevan bloqueo, a propósito:
 * <ul>
 *   <li>{@code SseHeartbeatScheduler}: cada instancia late a sus propias conexiones.</li>
 *   <li>{@code WorkflowWorker}: reparte la cola con {@code FOR UPDATE SKIP LOCKED},
 *       y conviene que trabajen todas las instancias a la vez.</li>
 * </ul>
 *
 * <p>Se usa la hora de la base ({@code usingDbTime}) y no la de cada servidor:
 * dos relojes desajustados harían que una instancia diera por vencido el bloqueo
 * de la otra antes de tiempo.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
