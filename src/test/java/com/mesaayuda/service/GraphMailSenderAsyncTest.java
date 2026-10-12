package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.config.AsyncConfig;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Esta es la prueba que fija el fallo real: el envío de correo DEBE ejecutarse
 * en el pool "notif-", nunca en el hilo que atiende la petición.
 * <p>
 * Con el código anterior el método marcado con {@code @Async} vivía en
 * {@code NotificationService} y se invocaba desde la propia
 * {@code NotificationService}, así que la llamada se resolvía con
 * {@code this}, esquivaba el proxy y se ejecutaba en el hilo del servidor. Cada
 * alta de ticket retenía entonces una conexión de la base durante los 20 s de
 * timeout de la llamada a Microsoft Graph.
 */
class GraphMailSenderAsyncTest {

    /**
     * Replica lo que hace la clase principal de la aplicación, que es quien
     * lleva {@code @EnableAsync}. Sin esto el contexto del test no crearía
     * ningún proxy asíncrono.
     */
    @org.springframework.context.annotation.Configuration
    @EnableAsync
    static class AsyncHabilitado {
    }

    /**
     * Los mocks se registran como singletons y no como definiciones de bean a
     * propósito: un mock de Mockito es una subclase de la clase real, y si la
     * registramos como bean Spring intentaría inyectarle sus campos
     * {@code @Autowired} heredados y fallaría al buscar el token service.
     */
    private AnnotationConfigApplicationContext contexto() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(AsyncConfig.class, AsyncHabilitado.class, GraphMailSender.class);
        ctx.getBeanFactory().registerSingleton("graphService", mock(MicrosoftGraphService.class));
        ctx.getBeanFactory().registerSingleton("graphConfigService", mock(MicrosoftGraphConfigService.class));
        ctx.refresh();
        return ctx;
    }

    private MicrosoftGraphConfigResponse configActivo() {
        return new MicrosoftGraphConfigResponse(
                id(1), "t", "c", true, "scope", "client_credentials", null,
                true, 60, true, "correo@coacmagisterio.fin.ec", null, null, "CONNECTED");
    }

    @Test
    @DisplayName("El envío en segundo plano corre en el pool notif-, no en el hilo llamador")
    void elEnvioOcurreFueraDelHiloLlamador() throws Exception {
        try (AnnotationConfigApplicationContext ctx = contexto()) {
            GraphMailSender sender = ctx.getBean(GraphMailSender.class);
            MicrosoftGraphService graph =
                    (MicrosoftGraphService) ctx.getBeanFactory().getBean("graphService");
            MicrosoftGraphConfigService config =
                    (MicrosoftGraphConfigService) ctx.getBeanFactory().getBean("graphConfigService");

            when(config.getConfig()).thenReturn(configActivo());

            // Capturamos el hilo desde dentro de la llamada real a Graph.
            AtomicReference<String> hiloDelEnvio = new AtomicReference<>();
            CountDownLatch enviado = new CountDownLatch(1);
            when(graph.sendMail(anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> {
                        hiloDelEnvio.set(Thread.currentThread().getName());
                        enviado.countDown();
                        return Map.of("success", true);
                    });

            String hiloLlamador = Thread.currentThread().getName();
            sender.enviarEnSegundoPlano("destino@coacmagisterio.fin.ec", "Asunto", "<p>Hola</p>");

            assertThat(enviado.await(10, TimeUnit.SECONDS))
                    .as("el correo debería haberse enviado en segundo plano")
                    .isTrue();

            assertThat(hiloDelEnvio.get())
                    .as("el envío no debe ejecutarse en el hilo de la petición")
                    .isNotNull()
                    .isNotEqualTo(hiloLlamador)
                    .startsWith("notif-");
        }
    }

    @Test
    @DisplayName("GraphMailSender queda proxied: es lo que permite que @Async funcione")
    void elBeanEstaProxied() {
        try (AnnotationConfigApplicationContext ctx = contexto()) {
            assertThat(AopUtils.isAopProxy(ctx.getBean(GraphMailSender.class)))
                    .as("sin proxy no hay @Async, y el envío bloquearía la petición")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("NotificationService ya no declara ningún método @Async (era código muerto)")
    void notificationServiceNoDeclaraAsync() {
        boolean declaraAsync = java.util.Arrays.stream(NotificationService.class.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(org.springframework.scheduling.annotation.Async.class));

        assertThat(declaraAsync)
                .as("un @Async en NotificationService volvería a ser inútil: sus propios métodos lo invocarían")
                .isFalse();
    }

    /** {@code @EnableAsync} vive en la clase principal; este test lo recuerda. */
    @Test
    @DisplayName("La aplicación principal habilita el procesamiento asíncrono")
    void laAplicacionHabilitaAsync() {
        assertThat(com.mesaayuda.MesaAyudaApplication.class.isAnnotationPresent(EnableAsync.class))
                .as("sin @EnableAsync ningún @Async del proyecto se ejecutaría en segundo plano")
                .isTrue();
    }
}
