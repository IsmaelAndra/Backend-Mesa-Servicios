package com.mesaayuda.scheduler;

import com.mesaayuda.entity.MailOutbox;
import com.mesaayuda.service.GraphMailSender;
import com.mesaayuda.service.MailOutboxPausa;
import com.mesaayuda.service.MailOutboxService;
import com.mesaayuda.service.NotificationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Envía la cola de correo respetando el límite del buzón.
 * <p>
 * Cada vuelta calcula el cupo que queda en el último minuto, reclama ese número
 * de correos y los manda uno a uno. Si Microsoft responde 429/503, se para todo
 * el tiempo que pida (Retry-After) y lo reclamado vuelve a la cola sin gastar
 * intentos. ShedLock: un solo proceso envía a la vez, porque el límite es del
 * buzón, no de cada instancia.
 * <p>
 * Agrupado: si una persona tiene varios avisos normales esperando (por ejemplo
 * en un pico), recibe un único correo resumen en vez de uno por aviso.
 */
@Component
public class ColaCorreoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ColaCorreoScheduler.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    private final MailOutboxService outbox;
    private final GraphMailSender sender;
    private final MailOutboxPausa pausa;
    private final NotificationService notificationService;

    @Value("${app.mail.outbox.lote:20}")
    private int lote;

    @Value("${app.mail.outbox.agrupar-desde:4}")
    private int agruparDesde;

    @Value("${app.mail.outbox.retencion-dias:30}")
    private int retencionDias;

    public ColaCorreoScheduler(MailOutboxService outbox, GraphMailSender sender, MailOutboxPausa pausa,
                               NotificationService notificationService) {
        this.outbox = outbox;
        this.sender = sender;
        this.pausa = pausa;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox.intervalo-ms:5000}", initialDelay = 15000)
    @SchedulerLock(name = "colaCorreo", lockAtMostFor = "PT2M", lockAtLeastFor = "PT2S")
    public void enviar() {
        if (pausa.enPausa() || !outbox.correoHabilitado()) {
            return;
        }
        long cupo = outbox.maxPorMinuto() - outbox.enviadosUltimoMinuto();
        if (cupo <= 0) {
            return;
        }
        List<MailOutbox> reclamados = outbox.reclamar((int) Math.min(cupo, Math.max(1, lote)));
        if (reclamados.isEmpty()) {
            return;
        }

        // Grupos: los urgentes van solos; los normales, por destinatario.
        List<List<MailOutbox>> envios = new ArrayList<>();
        Map<String, List<MailOutbox>> porPersona = new LinkedHashMap<>();
        for (MailOutbox m : reclamados) {
            if (m.getPriorityMail() < MailOutbox.PRIORIDAD_NORMAL) {
                envios.add(List.of(m));
            } else {
                porPersona.computeIfAbsent(m.getToMail().toLowerCase(), k -> new ArrayList<>()).add(m);
            }
        }
        for (List<MailOutbox> grupo : porPersona.values()) {
            if (grupo.size() >= Math.max(2, agruparDesde)) {
                envios.add(grupo);
            } else {
                grupo.forEach(m -> envios.add(List.of(m)));
            }
        }

        Set<MailOutbox> pendientesDeDevolver = new LinkedHashSet<>(reclamados);
        int enviados = 0;
        for (List<MailOutbox> envio : envios) {
            if (enviados >= cupo) {
                break; // los agrupados pueden traer más filas que el cupo, pero no más envíos
            }
            MailOutbox primero = envio.get(0);
            boolean resumen = envio.size() > 1;
            String asunto = resumen
                    ? "Tienes " + envio.size() + " avisos nuevos en la Mesa de Servicios"
                    : primero.getSubjectMail();
            String cuerpo = resumen ? cuerpoResumen(envio) : primero.getBodyMail();

            GraphMailSender.ResultadoEnvio r = sender.enviarConDetalle(primero.getToMail(), asunto, cuerpo);
            if (r.enviado()) {
                outbox.marcarEnviados(primero, envio.subList(1, envio.size()));
                envio.forEach(pendientesDeDevolver::remove);
                enviados++;
                continue;
            }
            if (r.deshabilitado()) {
                break; // se deshabilitó a mitad: todo vuelve a la cola
            }
            if (r.saturado()) {
                long espera = r.reintentarEnSegundos() == null ? 60 : r.reintentarEnSegundos();
                pausa.pausar(espera);
                log.warn("Microsoft Graph pidió esperar (HTTP {}): envío de correo en pausa {} s", r.status(), espera);
                break;
            }
            for (MailOutbox m : envio) {
                outbox.marcarFallo(m, r.mensaje(), false);
                pendientesDeDevolver.remove(m);
            }
        }
        if (!pendientesDeDevolver.isEmpty()) {
            outbox.devolver(pendientesDeDevolver);
        }
    }

    /** Limpieza diaria de lo ya enviado y antiguo. */
    @Scheduled(cron = "${app.mail.outbox.purga-cron:0 40 3 * * *}", zone = "America/Guayaquil")
    @SchedulerLock(name = "purgaColaCorreo", lockAtMostFor = "PT10M")
    public void purgar() {
        int n = outbox.purgar(retencionDias);
        if (n > 0) {
            log.info("Cola de correo: {} correos antiguos purgados", n);
        }
    }

    /** Un correo con la lista de avisos, con el mismo armazón que el resto. */
    private String cuerpoResumen(List<MailOutbox> grupo) {
        StringBuilder filas = new StringBuilder();
        for (MailOutbox m : grupo) {
            OffsetDateTime c = m.getCreatedAtMail() == null ? OffsetDateTime.now() : m.getCreatedAtMail();
            filas.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid #e5e7eb;font-size:14px;color:#111827;\">")
                    .append(escapar(m.getSubjectMail()))
                    .append("</td><td style=\"padding:10px 0 10px 12px;border-bottom:1px solid #e5e7eb;font-size:12px;color:#6b7280;white-space:nowrap;text-align:right;\">")
                    .append(c.atZoneSameInstant(ZONA).format(HORA))
                    .append("</td></tr>");
        }
        String contenido = "<p style=\"font-size:15px;color:#111827;margin:0 0 12px;\">Tienes <b>" + grupo.size()
                + " avisos nuevos</b> en la Mesa de Servicios. Para no llenarte la bandeja, te los enviamos juntos:</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + filas + "</table>"
                + "<p style=\"font-size:13px;color:#6b7280;margin:16px 0 0;\">Encontrarás el detalle de cada uno en la Mesa de Servicios.</p>";
        return notificationService.wrapEmailBody(contenido);
    }

    private static String escapar(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
