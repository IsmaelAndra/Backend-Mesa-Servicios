package com.mesaayuda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool de hilos del motor de workflows.
 *
 * <p><b>Va en su propia clase y no en {@link AsyncConfig} a propósito.</b>
 * Comparten beans, pero no contexto: si el motor se despliega y lo suyo falla al
 * arrancar, el correo de la Mesa de Servicios tiene que seguir saliendo. Y al
 * revés: el motor no arrastra al correo.
 *
 * <p>Además, el pool del correo ("taskExecutor") es para trabajo rápido y
 * constante. Este es lento y a ráfagas: una llamada a NEXA puede tardar 60
 * segundos, y un lote de diez ejecuciones que todas usen la IA deja este pool
 * ocupado un minuto entero. Si compartieran, un lote de automatizaciones
 * detendría los correos del help desk. Es el mismo problema que en la Fase 1 hizo
 * que el envío de correo se colgara dentro de la transacción, y por eso se
 * resuelve aquí desde el principio.
 */
@Configuration
public class WorkflowAsyncConfig {

    /**
     * Cola corta a propósito.
     *
     * <p>Si se llenara, el trabajo pendiente debe quedarse en la base esperando al
     * siguiente barrido del worker en lugar de acumularse en memoria. Perder una
     * ejecución de la cola es un error visible y recuperable; agotar la memoria del
     * servidor tumba el help desk entero.
     */
    @Bean(name = "workflowExecutor")
    public Executor workflowExecutor(WorkflowProperties properties) {
        int hilos = Math.max(1, properties.getThreads());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(hilos);
        executor.setMaxPoolSize(hilos);
        executor.setQueueCapacity(Math.max(1, properties.getMaxEjecucionesConcurrentes()));
        executor.setThreadNamePrefix("workflow-");
        executor.initialize();
        return executor;
    }
}