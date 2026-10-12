package com.mesaayuda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pools de trabajo en segundo plano.
 *
 * <p>Hay dos y van separados a propósito.
 *
 * <p>El de correo ("taskExecutor") lo usa {@code GraphMailSender.enviarEnSegundoPlano}
 * para no dejar una llamada a Microsoft Graph de 20 segundos dentro de la
 * transacción que notifica. Su trabajo es rápido y constante.
 *
 * <p>El del motor de workflows ("workflowExecutor") es lento y a ráfagas: una
 * llamada a NEXA puede tardar 60 segundos, y un lote de diez ejecuciones que todas
 * usen la IA deja ese pool ocupado un minuto entero.
 *
 * <p>Si compartieran pool, un lote de automatizaciones detendría el envío de
 * correos del help desk. Eso es exactamente lo que pasó en la Fase 1 con el
 * {@code @Async} sobre el propio servicio, y por eso aquí se evita desde el
 * principio en vez de discoverlo en producción.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();
        return executor;
    }
}