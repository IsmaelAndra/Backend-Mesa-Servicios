package com.mesaayuda.service;

import com.mesaayuda.dto.DailySummaryResponse;
import com.mesaayuda.dto.DashboardOperations;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resumen diario de la operación de la mesa de servicios enviado por correo
 * (Microsoft Graph). Se compone de indicadores de hoy y del estado actual de
 * la cola, y se distribuye a los administradores o a los destinatarios
 * configurados en {@code app.daily-summary.recipients}.
 */
@Service
public class DailySummaryService {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Value("${app.daily-summary.recipients:}")
    private String configuredRecipients;

    @Transactional(readOnly = true)
    public DailySummaryResponse send() {
        return send(null);
    }

    @Transactional(readOnly = true)
    public DailySummaryResponse send(String extraRecipient) {
        DashboardOperations ops = dashboardService.getOperations();
        List<String> recipients = resolveRecipients(extraRecipient);

        if (recipients.isEmpty()) {
            log.warn("Resumen diario sin destinatarios configurados; no se envió correo");
            return buildResponse(0, recipients, ops);
        }

        String subject = "Resumen diario · Mesa de Servicios · " + LocalDate.now(ZONE).format(FECHA);
        String html = buildHtml(ops);
        for (String to : recipients) {
            notificationService.sendSystemEmail(to, subject, html);
        }
        log.info("Resumen diario enviado a {} destinatario(s)", recipients.size());
        return buildResponse(recipients.size(), recipients, ops);
    }

    private DailySummaryResponse buildResponse(int sent, List<String> recipients, DashboardOperations ops) {
        return new DailySummaryResponse(
                sent, recipients,
                ops.createdToday(), ops.resolvedToday(),
                ops.openTickets(), ops.unassignedTickets(),
                ops.criticalTickets(), ops.slaAtRisk(), ops.slaBreachedOpen());
    }

    private List<String> resolveRecipients(String extraRecipient) {
        Set<String> recipients = new LinkedHashSet<>();

        if (configuredRecipients != null && !configuredRecipients.isBlank()) {
            for (String value : configuredRecipients.split(",")) {
                if (!value.isBlank()) {
                    recipients.add(value.trim());
                }
            }
        } else {
            List<User> admins = new ArrayList<>();
            admins.addAll(userRepository.findByRoleName("ADMIN"));
            admins.addAll(userRepository.findByRoleName("SUPER_ADMIN"));
            admins.stream()
                    .map(User::getCorporateEmailUser)
                    .filter(email -> email != null && !email.isBlank())
                    .map(String::trim)
                    .forEach(recipients::add);
        }

        if (extraRecipient != null && !extraRecipient.isBlank()) {
            recipients.add(extraRecipient.trim());
        }
        return new ArrayList<>(recipients);
    }

    private String buildHtml(DashboardOperations ops) {
        Map<String, Long> abiertosPorArea = ticketRepository.findOpenTickets().stream()
                .filter(t -> t.getAreaTicket() != null && t.getAreaTicket().getNameArea() != null)
                .collect(Collectors.groupingBy(t -> t.getAreaTicket().getNameArea(),
                        LinkedHashMap::new, Collectors.counting()));

        String filasArea = abiertosPorArea.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(6)
                .map(e -> "<tr>"
                        + "<td style=\"padding:8px 12px;border-bottom:1px solid #e5e7eb;\">" + escape(e.getKey()) + "</td>"
                        + "<td style=\"padding:8px 12px;border-bottom:1px solid #e5e7eb;text-align:right;font-weight:bold;\">"
                        + e.getValue() + "</td>"
                        + "</tr>")
                .collect(Collectors.joining());
        if (filasArea.isBlank()) {
            filasArea = "<tr><td colspan=\"2\" style=\"padding:8px 12px;color:#9ca3af;\">Sin tickets abiertos.</td></tr>";
        }

        String alerta = (ops.slaBreachedOpen() > 0 || ops.slaAtRisk() > 0)
                ? "<div style=\"margin-top:16px;padding:12px 16px;background:#fef2f2;border:1px solid #fecaca;border-radius:8px;color:#991b1b;\">"
                + "<b>Atención SLA:</b> " + ops.slaBreachedOpen() + " vencido(s) y "
                + ops.slaAtRisk() + " por vencer (próximas 2 horas).</div>"
                : "<div style=\"margin-top:16px;padding:12px 16px;background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;color:#166534;\">"
                + "<b>Sin alertas SLA:</b> no hay tickets vencidos ni por vencer en las próximas 2 horas.</div>";

        return """
            <h2 style="margin:0 0 4px;color:#062e86;">Resumen diario</h2>
            <p style="margin:0 0 18px;color:#6b7280;font-size:13px;">%s</p>

            <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:8px 0;">
              <tr>
                %s
                %s
                %s
                %s
              </tr>
            </table>

            <h3 style="margin:22px 0 8px;color:#062e86;font-size:15px;">Estado de la cola</h3>
            <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:8px 0;">
              <tr>
                %s
                %s
                %s
              </tr>
            </table>

            %s

            <h3 style="margin:22px 0 8px;color:#062e86;font-size:15px;">Tickets abiertos por área</h3>
            <table style="width:100%%;border-collapse:collapse;font-size:13px;">
              <thead>
                <tr style="background:#f3f4f6;color:#374151;">
                  <th style="text-align:left;padding:8px 12px;">Área</th>
                  <th style="text-align:right;padding:8px 12px;">Abiertos</th>
                </tr>
              </thead>
              <tbody>%s</tbody>
            </table>
            """.formatted(
                LocalDate.now(ZONE).format(FECHA),
                kpi("Creados hoy", ops.createdToday(), "#062e86"),
                kpi("Resueltos hoy", ops.resolvedToday(), "#16a34a"),
                kpi("Abiertos", ops.openTickets(), "#0369a1"),
                kpi("Sin asignar", ops.unassignedTickets(), "#b45309"),
                kpi("Críticos", ops.criticalTickets(), "#b91c1c"),
                kpi("SLA vencidos", ops.slaBreachedOpen(), "#b91c1c"),
                kpi("SLA por vencer", ops.slaAtRisk(), "#b45309"),
                alerta,
                filasArea);
    }

    private String kpi(String label, long value, String color) {
        return "<td style=\"width:25%;padding:14px 10px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;text-align:center;font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"font-size:24px;font-weight:bold;color:" + color + ";\">" + value + "</div>"
                + "<div style=\"font-size:12px;color:#6b7280;margin-top:2px;\">" + label + "</div>"
                + "</td>";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}