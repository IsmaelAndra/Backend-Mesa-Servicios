package com.mesaayuda.scheduler;

import com.mesaayuda.entity.MailOutbox;
import com.mesaayuda.service.GraphMailSender;
import com.mesaayuda.service.MailOutboxPausa;
import com.mesaayuda.service.MailOutboxService;
import com.mesaayuda.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lógica de envío de la cola de correo: cupo por minuto, agrupado por persona,
 * pausa cuando Microsoft pide esperar y reintentos ante un fallo normal.
 */
class ColaCorreoSchedulerTest {

    private MailOutboxService outbox;
    private GraphMailSender sender;
    private MailOutboxPausa pausa;
    private NotificationService notificationService;
    private ColaCorreoScheduler scheduler;

    @BeforeEach
    void setUp() {
        outbox = mock(MailOutboxService.class);
        sender = mock(GraphMailSender.class);
        pausa = new MailOutboxPausa();
        notificationService = mock(NotificationService.class);
        scheduler = new ColaCorreoScheduler(outbox, sender, pausa, notificationService);
        ReflectionTestUtils.setField(scheduler, "lote", 20);
        ReflectionTestUtils.setField(scheduler, "agruparDesde", 4);
        when(outbox.correoHabilitado()).thenReturn(true);
        when(outbox.maxPorMinuto()).thenReturn(25);
        when(outbox.enviadosUltimoMinuto()).thenReturn(0L);
        when(notificationService.wrapEmailBody(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    private static MailOutbox correo(String para, int prioridad) {
        MailOutbox m = new MailOutbox();
        m.setIdMail(UUID.randomUUID());
        m.setToMail(para);
        m.setSubjectMail("Aviso " + UUID.randomUUID().toString().substring(0, 4));
        m.setBodyMail("<p>cuerpo</p>");
        m.setPriorityMail(prioridad);
        m.setCreatedAtMail(OffsetDateTime.now());
        return m;
    }

    private static GraphMailSender.ResultadoEnvio ok() {
        return new GraphMailSender.ResultadoEnvio(true, false, 202, null, "ok");
    }

    @Test
    @DisplayName("con pocos avisos por persona, cada uno sale en su propio correo")
    void unoPorUno() {
        MailOutbox a = correo("ana@x.ec", 5);
        MailOutbox b = correo("luis@x.ec", 5);
        when(outbox.reclamar(anyInt())).thenReturn(List.of(a, b));
        when(sender.enviarConDetalle(anyString(), anyString(), anyString())).thenReturn(ok());

        scheduler.enviar();

        verify(sender, times(2)).enviarConDetalle(anyString(), anyString(), anyString());
        verify(outbox).marcarEnviados(eq(a), argThat(Collection::isEmpty));
        verify(outbox).marcarEnviados(eq(b), argThat(Collection::isEmpty));
        verify(outbox, never()).devolver(any());
    }

    @Test
    @DisplayName("en un pico, varios avisos para la misma persona salen en un solo correo resumen")
    void agrupaPorPersona() {
        List<MailOutbox> cinco = new ArrayList<>();
        for (int i = 0; i < 5; i++) cinco.add(correo("ana@x.ec", 5));
        when(outbox.reclamar(anyInt())).thenReturn(cinco);
        when(sender.enviarConDetalle(anyString(), anyString(), anyString())).thenReturn(ok());

        scheduler.enviar();

        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(sender, times(1)).enviarConDetalle(eq("ana@x.ec"), asunto.capture(), anyString());
        assertTrue(asunto.getValue().contains("5 avisos"));
        verify(outbox).marcarEnviados(eq(cinco.get(0)), argThat(c -> c.size() == 4));
    }

    @Test
    @DisplayName("lo urgente (claves, restablecer contraseña) nunca se agrupa")
    void urgenteNoSeAgrupa() {
        List<MailOutbox> lote = new ArrayList<>();
        for (int i = 0; i < 4; i++) lote.add(correo("ana@x.ec", MailOutbox.PRIORIDAD_URGENTE));
        when(outbox.reclamar(anyInt())).thenReturn(lote);
        when(sender.enviarConDetalle(anyString(), anyString(), anyString())).thenReturn(ok());

        scheduler.enviar();

        verify(sender, times(4)).enviarConDetalle(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("si Microsoft responde 429, se pausa el envío y lo reclamado vuelve sin gastar intentos")
    void pausaAnteSaturacion() {
        MailOutbox a = correo("ana@x.ec", 5);
        MailOutbox b = correo("luis@x.ec", 5);
        when(outbox.reclamar(anyInt())).thenReturn(List.of(a, b));
        when(sender.enviarConDetalle(anyString(), anyString(), anyString()))
                .thenReturn(new GraphMailSender.ResultadoEnvio(false, false, 429, 30L, "Too many requests"));

        scheduler.enviar();

        assertTrue(pausa.enPausa());
        verify(sender, times(1)).enviarConDetalle(anyString(), anyString(), anyString());
        verify(outbox, never()).marcarFallo(any(), any(), anyBoolean());
        verify(outbox).devolver(argThat(c -> c.size() == 2));

        // En pausa no se vuelve a reclamar nada.
        scheduler.enviar();
        verify(outbox, times(1)).reclamar(anyInt());
    }

    @Test
    @DisplayName("un fallo normal gasta un intento (el servicio lo reprograma o lo da por fallido)")
    void falloNormal() {
        MailOutbox a = correo("ana@x.ec", 5);
        when(outbox.reclamar(anyInt())).thenReturn(List.of(a));
        when(sender.enviarConDetalle(anyString(), anyString(), anyString()))
                .thenReturn(new GraphMailSender.ResultadoEnvio(false, false, 400, null, "Destinatario inválido"));

        scheduler.enviar();

        verify(outbox).marcarFallo(a, "Destinatario inválido", false);
        verify(outbox, never()).devolver(any());
    }

    @Test
    @DisplayName("sin cupo en el último minuto no se reclama nada")
    void respetaElCupo() {
        when(outbox.enviadosUltimoMinuto()).thenReturn(25L);

        scheduler.enviar();

        verify(outbox, never()).reclamar(anyInt());
    }

    @Test
    @DisplayName("solo reclama lo que cabe en el cupo que queda")
    void reclamaSoloElCupo() {
        when(outbox.enviadosUltimoMinuto()).thenReturn(22L);
        when(outbox.reclamar(anyInt())).thenReturn(List.of());

        scheduler.enviar();

        ArgumentCaptor<Integer> n = ArgumentCaptor.forClass(Integer.class);
        verify(outbox).reclamar(n.capture());
        assertEquals(3, n.getValue());
    }
}
