package com.mesaayuda.service;

import com.mesaayuda.entity.*;
import com.mesaayuda.repository.NotificationRepository;
import com.mesaayuda.repository.NotificationTemplateRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private SmtpService smtpService;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    // ============================ DISPARADORES ============================

    public void notifyTicketCreated(Ticket ticket) {
        String subject = template("TICKET_CREADO", "subject", "Ticket #%d creado".formatted(ticket.getIdTicket()));
        String body = template("TICKET_CREADO", "body", Map.of(
                "id", ticket.getIdTicket(),
                "title", ticket.getTitleTicket(),
                "status", ticket.getStatusTicket().getNameStatus(),
                "priority", ticket.getPriorityTicket().getNamePriority()));
        sendNotification(ticket, ticket.getUserTicket(), "TICKET_CREADO", subject, body);
    }

    public void notifyTicketAssigned(Ticket ticket) {
        if (ticket.getAgentTicket() == null) return;
        User receiver = ticket.getAgentTicket();
        String subject = template("TICKET_ASIGNADO", "subject", "Ticket #%d asignado".formatted(ticket.getIdTicket()));
        String body = template("TICKET_ASIGNADO", "body", Map.of(
                "id", ticket.getIdTicket(),
                "title", ticket.getTitleTicket(),
                "agente", receiver.getCorporateEmailUser()));
        sendNotification(ticket, receiver, "TICKET_ASIGNADO", subject, body);
    }

    public void notifyTicketResolved(Ticket ticket) {
        notifySimple(ticket, ticket.getUserTicket(), "TICKET_RESUELTO",
                "Ticket #%d resuelto".formatted(ticket.getIdTicket()), ticket.getTitleTicket());
        notifyRatingRequest(ticket);
    }

    public void notifyTicketClosed(Ticket ticket) {
        notifySimple(ticket, ticket.getUserTicket(), "TICKET_CERRADO",
                "Ticket #%d cerrado".formatted(ticket.getIdTicket()), ticket.getTitleTicket());
    }

    public void notifyRatingRequest(Ticket ticket) {
        String subject = template("RATING_REQUEST", "subject", "Califique su ticket #%d".formatted(ticket.getIdTicket()));
        String body = template("RATING_REQUEST", "body", Map.of(
                "id", ticket.getIdTicket(),
                "title", ticket.getTitleTicket()));
        sendNotification(ticket, ticket.getUserTicket(), "RATING_REQUEST", subject, body);
    }

    public void notifySlaWarning(TicketSla ticketSla, int percentage) {
        String subject = template("SLA_WARNING", "subject", "SLA por vencer - Ticket #%d".formatted(ticketSla.getTicket().getIdTicket()));
        String body = template("SLA_WARNING", "body", Map.of(
                "id", ticketSla.getTicket().getIdTicket(),
                "percentage", percentage));
        sendNotification(ticketSla.getTicket(), ticketSla.getTicket().getAgentTicket(),
                "SLA_WARNING", subject, body);
    }

    public void notifySlaBreached(TicketSla ticketSla) {
        String subject = template("SLA_BREACHED", "subject", "SLA vencido - Ticket #%d".formatted(ticketSla.getTicket().getIdTicket()));
        String body = template("SLA_BREACHED", "body", Map.of(
                "id", ticketSla.getTicket().getIdTicket()));
        sendNotification(ticketSla.getTicket(), ticketSla.getTicket().getAgentTicket(),
                "SLA_BREACHED", subject, body);
    }

    public void notifyEscalation(Ticket ticket, SlaEscalation escalation) {
        User receiver = escalation.getDestinationUser() != null
                ? escalation.getDestinationUser()
                : (ticket.getAgentTicket() != null ? ticket.getAgentTicket() : ticket.getUserTicket());
        String subject = template("ESCALATION", "subject", "Ticket #%d escalado".formatted(ticket.getIdTicket()));
        String body = template("ESCALATION", "body", Map.of(
                "id", ticket.getIdTicket(),
                "action", escalation.getActionType()));
        sendNotification(ticket, receiver, "ESCALATION", subject, body);
    }

    public void notifyMonthlyEvaluation(User agent) {
        String subject = template("MONTHLY_EVALUATION", "subject", "Evaluación mensual disponible");
        String body = template("MONTHLY_EVALUATION", "body", Map.of("nombre", agent.getCorporateEmailUser()));
        sendNotification(null, agent, "MONTHLY_EVALUATION", subject, body);
    }

    public void notifyTaskAssigned(Task task) {
        if (task.getAssignedUser() == null) return;
        User receiver = task.getAssignedUser();
        String fecha = task.getStartTask() != null
                ? task.getStartTask().toLocalDate().toString()
                : (task.getDueTask() != null ? task.getDueTask().toLocalDate().toString() : "por confirmar");
        String hora = task.getStartTask() != null
                ? task.getStartTask().toLocalTime().toString()
                : (task.getDueTask() != null ? task.getDueTask().toLocalTime().toString() : "—");
        String subject = "Tarea asignada: " + task.getTitleTask();
        String body = "Se le ha asignado la siguiente tarea:<br/><br/>"
                + "<b>Tarea:</b> " + task.getTitleTask() + "<br/>"
                + (task.getDescriptionTask() != null && !task.getDescriptionTask().isBlank()
                        ? "<b>Descripción:</b> " + task.getDescriptionTask() + "<br/>" : "")
                + "<b>Fecha:</b> " + fecha + "<br/>"
                + "<b>Hora:</b> " + hora + "<br/>"
                + "<b>Estado:</b> " + task.getStatusTask() + "<br/><br/>"
                + "Por favor tome en cuenta la hora acordada y llegue preparado a la cita.";
        sendNotification(null, receiver, "TASK_ASIGNADA", subject, body);
    }

    public void notifyNewAccount(User user, String generatedPassword) {
        String subject = "Credenciales de acceso - Mesa de Ayuda COAC";
        String body = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333'>"
                + "<h2 style='color:#062e86'>Bienvenido/a a la Mesa de Ayuda COAC</h2>"
                + "<p>Estimado/a <b>" + user.getCorporateEmailUser() + "</b>:</p>"
                + "<p>Se ha creado su cuenta de acceso. Su <b>contraseña temporal</b> es:</p>"
                + "<div style='margin:16px 0;padding:14px;background:#f3f4f6;border-radius:8px;"
                + "font-size:20px;font-weight:bold;text-align:center;letter-spacing:1px'>"
                + generatedPassword + "</div>"
                + "<p>Al iniciar sesión deberá cambiarla por una contraseña personalizada.</p>"
                + "<p style='color:#999;font-size:12px'>Mesa de Ayuda COAC</p>"
                + "</div>";
        sendEmailAsync(user.getCorporateEmailUser(), subject, body);
    }

    public void notifyPasswordReset(User user, String tempPassword) {
        String subject = "Restablecimiento de contraseña - Mesa de Ayuda COAC";
        String body = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333'>"
                + "<h2 style='color:#062e86'>Restablecimiento de contraseña</h2>"
                + "<p>Estimado/a <b>" + user.getCorporateEmailUser() + "</b>:</p>"
                + "<p>Se ha restablecido su contraseña. Su <b>contraseña temporal</b> de acceso es:</p>"
                + "<div style='margin:16px 0;padding:14px;background:#f3f4f6;border-radius:8px;"
                + "font-size:20px;font-weight:bold;text-align:center;letter-spacing:1px'>"
                + tempPassword + "</div>"
                + "<p>Al iniciar sesión deberá cambiarla por una personalizada.</p>"
                + "<p style='color:#999;font-size:12px'>Mesa de Ayuda COAC</p>"
                + "</div>";
        sendEmailAsync(user.getCorporateEmailUser(), subject, body);
    }

    // ============================ NÚCLEO ============================

    private void notifySimple(Ticket ticket, User receiver, String type,
                              String defaultSubject, String title) {
        String subject = templateOrDefault(type, "subject", defaultSubject);
        String body = templateOrDefault(type, "body", "Ticket: " + (title == null ? "" : title));
        sendNotification(ticket, receiver, type, subject, body);
    }

    public void sendNotification(Ticket ticket, User receiver, String type,
                                 String subject, String body) {
        if (receiver == null) return;

        Notification notification = new Notification();
        notification.setTicket(ticket);
        notification.setUser(receiver);
        notification.setTypeNotification(type);
        notification.setSubjectNotification(subject);
        notification.setMessageNotification(body);
        notification.setStatusNotification("PENDING");
        notificationRepository.save(notification);

        if (mailEnabled) {
            sendEmailAsync(receiver.getCorporateEmailUser(), subject, body);
        }
    }

    @Async("taskExecutor")
    public void sendEmailAsync(String to, String subject, String bodyHtml) {
        sendEmail(to, subject, bodyHtml);
    }

    public void sendEmail(String to, String subject, String bodyHtml) {
        if (!mailEnabled) {
            log.debug("Email deshabilitado. Destinatario={}, Asunto={}", to, subject);
            return;
        }
        try {
            JavaMailSender mailSender = smtpService.buildSender();
            String from = smtpService.fromAddress();
            String fromName = smtpService.displayName();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(new InternetAddress(from, fromName, "UTF-8"));
            } else {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(bodyHtml, true);
            mailSender.send(message);
            log.info("Email enviado a {}", to);
        } catch (Exception e) {
            log.error("Error enviando email a {}: {}", to, e.getMessage());
        }
    }

    public void processPendingNotifications() {
        List<Notification> pending = notificationRepository
                .findByStatusNotificationOrderByCreatedAtNotificationAsc("PENDING");
        for (Notification n : pending) {
            if (n.getUser() == null) continue;
            try {
                sendEmail(n.getUser().getCorporateEmailUser(),
                        n.getSubjectNotification(), n.getMessageNotification());
                n.setStatusNotification("SENT");
                n.setSentAt(OffsetDateTime.now());
            } catch (Exception ex) {
                n.setStatusNotification("FAILED");
                log.error("Fallo al procesar notificación #{}", n.getIdNotification(), ex);
            }
            notificationRepository.save(n);
        }
    }

    // ============================ PLANTILLAS ============================

    private String template(String name, String fieldExpr, String fallback) {
        return templateOrDefault(name, fieldExpr, fallback);
    }

    private String templateOrDefault(String name, String fieldExpr, String fallback) {
        return templateRepository.findByNameTemplateAndActiveTemplateTrue(name)
                .map(NotificationTemplate::getBodyTemplate)
                .orElse(fallback);
    }

    private String template(String name, String fieldExpr, Map<String, Object> variables) {
        String fallback = name + ": " + variables.toString();
        Context context = new Context();
        variables.forEach(context::setVariable);
        context.setVariable("appName", "Mesa de Ayuda COAC");
        try {
            return templateEngine.process("notification", context);
        } catch (Exception e) {
            log.warn("Plantilla Thymeleaf no disponible para {}, usando fallback", name);
            return templateOrDefault(name, fieldExpr, fallback);
        }
    }
}