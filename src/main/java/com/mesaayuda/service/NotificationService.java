package com.mesaayuda.service;

import com.mesaayuda.entity.MailOutbox;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import com.mesaayuda.exception.MailDeliveryException;
import com.mesaayuda.entity.*;
import com.mesaayuda.repository.AnswerRepository;
import com.mesaayuda.repository.AttachRepository;
import com.mesaayuda.repository.BrandingConfigRepository;
import com.mesaayuda.repository.TicketAuditRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.repository.NotificationRepository;
import com.mesaayuda.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private MicrosoftGraphConfigService graphConfigService;

    /**
     * El envío real de correo vive en otro bean a propósito: desde aquí las
     * llamadas sí atraviesan el proxy y {@code @Async} funciona. Antes el metodo
     * marcado como asíncrono estaba en esta misma clase y se llamaba a sí
     * mismo, así que nunca salía del hilo de la petición.
     */
    @Autowired
    private GraphMailSender graphMailSender;

    @Autowired
    private MailOutboxService mailOutboxService;

    @Autowired
    private BrandingConfigRepository brandingConfigRepository;

    // Datos extra de las plantillas (SLA, contadores, solución, quién asigna).
    // Se usan con tolerancia a null: las pruebas unitarias crean el servicio sin ellos.
    @Autowired(required = false)
    private TicketSlaRepository ticketSlaRepository;

    @Autowired(required = false)
    private AnswerRepository answerRepository;

    @Autowired(required = false)
    private AttachRepository attachRepository;

    @Autowired(required = false)
    private TicketAuditRepository ticketAuditRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale ES = Locale.forLanguageTag("es-EC");

    @Autowired
    private SseEventBroker sseEventBroker;

    @Value("${app.public-base-url:}")
    private String appBaseUrl;

    /**
     * Base del frontend, distinta de {@link #appBaseUrl}: los correos llevan
     * enlaces y el usuario navega en la SPA, no en la API. Sin esta variable los
     * botones CTA y los enlaces del pie se vaciaban porque sus marcadores no
     * tenían de dónde sacarse.
     */
    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    // ============================ DISPARADORES ============================

    /**
     * Variables comunes de cualquier evento de ticket. Las plantillas siembradas
     * piden solicitante, area, categoria, prioridad, estado y SLA; si el
     * disparador no las daba, el marcador salía vacío en la notificación.
     */
    private Map<String, Object> ticketVars(Ticket ticket) {
        Map<String, Object> v = new LinkedHashMap<>();
        // [id] es el número que ve la persona ("ticket 43"); el uuid va aparte
        // en ticketId, que es el que lleva el enlace al ticket.
        v.put("id", numeroDe(ticket));
        v.put("numero", numeroDe(ticket));
        v.put("ticketId", ticket.getIdTicket());
        v.put("title", ticket.getTitleTicket());
        v.put("titulo", ticket.getTitleTicket());
        // La descripción es HTML del editor (párrafos, imágenes pegadas). Al
        // escaparla llegaba al correo con las etiquetas a la vista; se pasa a texto.
        v.put("descripcion", recortar(textoDelChat(ticket.getDescriptionTicket()), 800));
        v.put("estado", nombre(ticket.getStatusTicket() == null ? null : ticket.getStatusTicket().getNameStatus()));
        v.put("status", v.get("estado"));
        v.put("prioridad", nombre(ticket.getPriorityTicket() == null ? null : ticket.getPriorityTicket().getNamePriority()));
        v.put("priority", v.get("prioridad"));
        v.put("categoria", nombre(ticket.getCategoryTicket() == null ? null : ticket.getCategoryTicket().getNameCategory()));
        v.put("area", nombre(ticket.getAreaTicket() == null ? null : ticket.getAreaTicket().getNameArea()));
        v.put("tipo", nombre(ticket.getTypeTicket() == null ? null : ticket.getTypeTicket().getNameTicketType()));
        v.put("solicitante", nameOf(ticket.getUserTicket()));
        v.put("agente", ticket.getAgentTicket() == null ? "Sin asignar" : nameOf(ticket.getAgentTicket()));
        v.put("ticket", v.get("id"));
        v.put("asunto", v.get("titulo"));
        v.put("correoSolicitante", ticket.getUserTicket() == null ? "" : nombre(ticket.getUserTicket().getCorporateEmailUser()));
        v.put("fechaCreacion", fechaHora(ticket.getCreatedAtTicket()));
        v.put("fechaAsignacion", fechaHora(OffsetDateTime.now()));
        v.put("fechaActualizacion", fechaHora(OffsetDateTime.now()));
        OffsetDateTime fin = ticket.getClosingDateTicket() != null ? ticket.getClosingDateTicket() : OffsetDateTime.now();
        v.put("fechaResolucion", fechaHora(fin));
        v.put("fechaCierre", fechaHora(fin));
        v.put("tiempoTotal", ticket.getCreatedAtTicket() == null ? ""
                : duracion(Duration.between(ticket.getCreatedAtTicket(), fin).toMinutes()));
        v.put("asignadoPor", quienActua("Asignación automática"));
        varsSla(ticket, v);
        varsConversacion(ticket, v);
        return v;
    }

    /**
     * Variables del SLA: [slaTiempo]/[sla] (plazo de resolución de la política),
     * [fechaLimite], [tiempoTranscurrido], [tiempoRestante] y [tiempoVencido].
     */
    private void varsSla(Ticket ticket, Map<String, Object> v) {
        v.put("slaTiempo", "el acuerdo de nivel de servicio vigente");
        v.put("sla", "");
        v.put("fechaLimite", "");
        v.put("tiempoTranscurrido", "");
        v.put("tiempoRestante", "");
        v.put("tiempoVencido", "");
        TicketSla sla = slaDe(ticket);
        if (sla == null) {
            return;
        }
        Integer minutos = sla.getSlaPolicy() == null ? null : sla.getSlaPolicy().getResolutionTimeMinutes();
        if (minutos != null && minutos > 0) {
            v.put("slaTiempo", duracion(minutos));
            v.put("sla", duracion(minutos));
        }
        OffsetDateTime limite = sla.getResolutionDueAt();
        OffsetDateTime ahora = OffsetDateTime.now();
        if (limite != null) {
            v.put("fechaLimite", fechaHora(limite));
            long resta = Duration.between(ahora, limite).toMinutes();
            v.put("tiempoRestante", resta > 0 ? duracion(resta) : "Vencido");
            v.put("tiempoVencido", resta < 0 ? duracion(-resta) : "");
        }
        if (ticket.getCreatedAtTicket() != null) {
            v.put("tiempoTranscurrido", duracion(Duration.between(ticket.getCreatedAtTicket(), ahora).toMinutes()));
        }
    }

    /**
     * Variables de la conversación: [solucion]/[resumenAtencion] (última respuesta
     * pública del personal), [totalMensajes], [totalAdjuntos], [totalCambios],
     * [valorAnterior]/[valorNuevo] (último cambio registrado del ticket).
     */
    private void varsConversacion(Ticket ticket, Map<String, Object> v) {
        v.put("solucion", "");
        v.put("resumenAtencion", "");
        v.put("totalMensajes", "0");
        v.put("totalAdjuntos", "0");
        v.put("totalCambios", "0");
        v.put("valorAnterior", "");
        v.put("valorNuevo", "");
        if (ticket.getIdTicket() == null) {
            return;
        }
        try {
            if (answerRepository != null) {
                v.put("totalMensajes", String.valueOf(answerRepository
                        .countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(ticket.getIdTicket())));
                String solucion = answerRepository
                        .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                                ticket.getIdTicket(), org.springframework.data.domain.PageRequest.of(0, 10))
                        .stream()
                        .filter(a -> a.getUserAnswer() != null && ticket.getUserTicket() != null
                                && !a.getUserAnswer().getIdUser().equals(ticket.getUserTicket().getIdUser()))
                        .map(a -> recortar(textoDelChat(a.getMessageAnswer()), 600))
                        .filter(t -> !t.isBlank())
                        .findFirst().orElse("");
                v.put("solucion", solucion);
                v.put("resumenAtencion", solucion);
            }
            if (attachRepository != null) {
                v.put("totalAdjuntos", String.valueOf(attachRepository
                        .findByTicketAttachIdTicketAndDeletedAttachFalse(ticket.getIdTicket()).size()));
            }
            if (ticketAuditRepository != null) {
                List<TicketAudit> cambios = ticketAuditRepository
                        .findByTicketIdTicketOrderByCreatedAtAuditDesc(ticket.getIdTicket());
                v.put("totalCambios", String.valueOf(cambios.stream()
                        .filter(c -> "STATUS_CHANGE".equals(c.getActionType())).count()));
                cambios.stream()
                        .filter(c -> c.getNewValue() != null && !c.getActionType().startsWith("ANSWER"))
                        .findFirst()
                        .ifPresent(c -> {
                            v.put("valorAnterior", nombre(c.getOldValue()));
                            v.put("valorNuevo", nombre(c.getNewValue()));
                        });
            }
        } catch (RuntimeException e) {
            log.debug("Variables de conversación no disponibles para el ticket {}: {}",
                    ticket.getIdTicket(), e.getMessage());
        }
    }

    private TicketSla slaDe(Ticket ticket) {
        if (ticketSlaRepository == null || ticket.getIdTicket() == null) {
            return null;
        }
        try {
            return ticketSlaRepository.findByTicketIdTicket(ticket.getIdTicket()).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Nombre de quien hace la petición en curso, o el texto dado si es un proceso automático. */
    private String quienActua(String siNadie) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || userRepository == null
                    || auth.getName() == null || !auth.getName().contains("@")) {
                return siNadie;
            }
            return userRepository.findByCorporateEmailUser(auth.getName())
                    .map(this::nameOf).orElse(siNadie);
        } catch (RuntimeException e) {
            return siNadie;
        }
    }

    /** "30 min", "4 h", "2 d 3 h": igual que duracionLegible() del frontend. */
    static String duracion(long minutos) {
        if (minutos < 60) {
            return Math.max(minutos, 0) + " min";
        }
        long horas = minutos / 60;
        long resto = minutos % 60;
        if (horas < 24) {
            return resto == 0 ? horas + " h" : horas + " h " + resto + " min";
        }
        long dias = horas / 24;
        long h = horas % 24;
        return h == 0 ? dias + " d" : dias + " d " + h + " h";
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() <= max ? texto : texto.substring(0, max).trim() + "…";
    }

    /**
     * Todas las variables del ticket ([id], [titulo], [estado], [prioridad],
     * [categoria], [area], [tipo], [solicitante], [agente]...) más [nombre] de
     * quien recibe el correo. Antes varios eventos (actualizado, resuelto,
     * cerrado, SLA, escalado, calificación) solo pasaban [id] y [titulo], y lo
     * demás que ofrece el editor de plantillas salía vacío en el correo.
     */
    private Map<String, Object> varsTicket(Ticket ticket, User receiver) {
        Map<String, Object> v = new HashMap<>(ticketVars(ticket));
        v.put("nombre", nameOf(receiver));
        return v;
    }

    /** Acción de una escalación en español, como se ve en la pantalla de SLA. */
    private static String accionEnEspanol(String accion) {
        if (accion == null) return "";
        return switch (accion) {
            case "NOTIFY_SUPERVISOR" -> "Notificar al supervisor";
            case "ESCALATE" -> "Escalar";
            case "REASSIGN" -> "Reasignar";
            default -> accion;
        };
    }

    private String nombre(String valor) {
        return valor == null ? "" : valor;
    }

    private String fecha(OffsetDateTime momento) {
        return momento == null ? "" : momento.atZoneSameInstant(ZONA).format(FECHA);
    }

    private String fechaHora(OffsetDateTime momento) {
        return momento == null ? "" : momento.atZoneSameInstant(ZONA).format(FECHA_HORA);
    }

    public void notifyTicketCreated(Ticket ticket) {
        Map<String, Object> variables = varsTicket(ticket, ticket.getUserTicket());
        String subject = subjectTemplate("TICKET_CREADO", variables,
                "Ticket #%d creado".formatted(ticket.getNumberTicket()));
        String body = bodyTemplate("TICKET_CREADO", variables,
                "<p>Estimado/a <b>%s</b>, su ticket <b>#%d - %s</b> fue creado correctamente.</p>"
                        .formatted(escaparHtml(nameOf(ticket.getUserTicket())), ticket.getNumberTicket(),
                                escaparHtml(ticket.getTitleTicket())));
        sendNotification(ticket, ticket.getUserTicket(), "TICKET_CREADO", subject, body);
    }

    public void notifyTicketAssigned(Ticket ticket) {
        if (ticket.getAgentTicket() == null) return;
        User receiver = ticket.getAgentTicket();
        // [nombre] es quien recibe el correo (el agente) y [agente] su nombre, no
        // su correo: antes [nombre] salía vacío y [agente] con la dirección.
        Map<String, Object> variables = varsTicket(ticket, receiver);
        String subject = subjectTemplate("TICKET_ASIGNADO", variables,
                "Ticket #%d asignado".formatted(ticket.getNumberTicket()));
        String body = bodyTemplate("TICKET_ASIGNADO", variables,
                "<p>Se le ha asignado el ticket <b>#%d - %s</b>.</p>"
                        .formatted(ticket.getNumberTicket(), escaparHtml(ticket.getTitleTicket())));
        sendNotification(ticket, receiver, "TICKET_ASIGNADO", subject, body);
    }

    public void notifyTicketUpdated(Ticket ticket, User receiver, String mensajeHtml) {
        String message = textoDelChat(mensajeHtml);
        Map<String, Object> variables = varsTicket(ticket, receiver);
        variables.put("respuesta", message == null ? "" : message);
        variables.put("mensaje", message == null ? "" : message);
        variables.put("comentario", message == null ? "" : message);
        variables.put("observacion", message == null ? "" : message);
        String subject = subjectTemplate("TICKET_ACTUALIZADO", variables,
                "Ticket #%d actualizado".formatted(ticket.getNumberTicket()));
        String body = bodyTemplate("TICKET_ACTUALIZADO", variables,
                "<p>El ticket <b>#%d - %s</b> tiene una nueva respuesta:</p><p>%s</p>"
                        .formatted(ticket.getNumberTicket(), escaparHtml(ticket.getTitleTicket()),
                                conSaltos(message)));
        sendNotification(ticket, receiver, "TICKET_ACTUALIZADO", subject, body);
    }

    /**
     * Aviso de respuesta nueva escrita en el chat.
     *
     * Antes una respuesta en el hilo se notificaba con el mismo tipo
     * TICKET_ACTUALIZADO que un cambio de estado o de prioridad, asi que el
     * icono del panel no distinguia "me contestaron" de "movieron el ticket".
     * Este tipo es propio y solo se usa para mensajes del chat.
     *
     * Si no existe plantilla CHAT_MENSAJE, subjectTemplate y bodyTemplate caen
     * al texto por defecto de abajo, que ya es un mensaje utilizable.
     */
    public void notifyChatMessage(Ticket ticket, User receiver, String mensajeHtml, String autor) {
        if (receiver == null || ticket == null) return;
        // El chat guarda el HTML del editor ("<p>Prueba</p><p><br></p>"). Al
        // escaparlo para el correo llegaba con las etiquetas a la vista.
        String message = textoDelChat(mensajeHtml);
        Map<String, Object> variables = varsTicket(ticket, receiver);
        variables.put("respuesta", message == null ? "" : message);
        variables.put("mensaje", message == null ? "" : message);
        variables.put("comentario", message == null ? "" : message);
        // [autor] es quien escribió y [nombre] quien recibe el correo ("Estimado/a
        // [nombre]: [autor] ha escrito..."). Antes [nombre] era el autor y el
        // correo saludaba a quien lo había escrito.
        variables.put("autor", autor == null || autor.isBlank() ? "Un usuario" : autor);
        variables.put("fechaMensaje", fechaHora(OffsetDateTime.now()));

        String asunto = autor == null || autor.isBlank()
                ? "Nuevo mensaje en el ticket #%d".formatted(ticket.getNumberTicket())
                : "Nuevo mensaje de %s en el ticket #%d".formatted(autor, ticket.getNumberTicket());
        String cuerpo = autor == null || autor.isBlank()
                ? "<p>Hay un mensaje nuevo en el ticket <b>#%d - %s</b>.</p><p>%s</p>"
                        .formatted(ticket.getNumberTicket(), escaparHtml(ticket.getTitleTicket()),
                                conSaltos(message))
                : "<p><b>%s</b> escribió en el ticket <b>#%d - %s</b>:</p><p>%s</p>"
                        .formatted(escaparHtml(autor), ticket.getNumberTicket(),
                                escaparHtml(ticket.getTitleTicket()),
                                conSaltos(message));

        sendNotification(ticket, receiver, "CHAT_MENSAJE",
                subjectTemplate("CHAT_MENSAJE", variables, asunto),
                bodyTemplate("CHAT_MENSAJE", variables, cuerpo));
    }

    public void notifyTicketResolved(Ticket ticket) {
        notifySimple(ticket, ticket.getUserTicket(), "TICKET_RESUELTO",
                "Ticket #%d resuelto".formatted(ticket.getNumberTicket()), ticket.getTitleTicket());
        notifyRatingRequest(ticket);
    }

    public void notifyTicketClosed(Ticket ticket) {
        notifySimple(ticket, ticket.getUserTicket(), "TICKET_CERRADO",
                "Ticket #%d cerrado".formatted(ticket.getNumberTicket()), ticket.getTitleTicket());
    }

    public void notifyRatingRequest(Ticket ticket) {
        Map<String, Object> variables = varsTicket(ticket, ticket.getUserTicket());
        String subject = subjectTemplate("RATING_REQUEST", variables,
                "Califique su ticket #%d".formatted(ticket.getNumberTicket()));
        String body = bodyTemplate("RATING_REQUEST", variables,
                "<p>Estimado/a <b>%s</b>, le invitamos a calificar la atención recibida en su ticket <b>#%d</b>.</p>"
                        .formatted(nameOf(ticket.getUserTicket()), ticket.getNumberTicket()));
        sendNotification(ticket, ticket.getUserTicket(), "RATING_REQUEST", subject, body);
    }

    public void notifySlaWarning(TicketSla ticketSla, int percentage) {
        Map<String, Object> variables = varsTicket(ticketSla.getTicket(), ticketSla.getTicket().getAgentTicket());
        variables.put("percentage", percentage);
        variables.put("porcentaje", percentage);
        variables.put("nombre", nameOf(ticketSla.getTicket().getAgentTicket()));
        String subject = subjectTemplate("SLA_WARNING", variables,
                "SLA por vencer - Ticket #%d".formatted(ticketSla.getTicket().getNumberTicket()));
        String body = bodyTemplate("SLA_WARNING", variables,
                "<p>El SLA del ticket <b>#%d</b> está al %d%%.</p>"
                        .formatted(ticketSla.getTicket().getNumberTicket(), percentage));
        sendNotification(ticketSla.getTicket(), ticketSla.getTicket().getAgentTicket(),
                "SLA_WARNING", subject, body);
    }

    public void notifySlaBreached(TicketSla ticketSla) {
        Map<String, Object> variables = varsTicket(ticketSla.getTicket(), ticketSla.getTicket().getAgentTicket());
        String subject = subjectTemplate("SLA_BREACHED", variables,
                "SLA vencido - Ticket #%d".formatted(ticketSla.getTicket().getNumberTicket()));
        String body = bodyTemplate("SLA_BREACHED", variables,
                "<p>El SLA del ticket <b>#%d</b> se ha vencido.</p>"
                        .formatted(ticketSla.getTicket().getNumberTicket()));
        sendNotification(ticketSla.getTicket(), ticketSla.getTicket().getAgentTicket(),
                "SLA_BREACHED", subject, body);
    }

    public void notifyEscalation(Ticket ticket, SlaEscalation escalation) {
        User receiver = escalation.getDestinationUser() != null
                ? escalation.getDestinationUser()
                : (ticket.getAgentTicket() != null ? ticket.getAgentTicket() : ticket.getUserTicket());
        Map<String, Object> variables = varsTicket(ticket, receiver);
        variables.put("action", accionEnEspanol(escalation.getActionType()));
        variables.put("accion", accionEnEspanol(escalation.getActionType()));
        variables.put("fechaEscalado", fechaHora(OffsetDateTime.now()));
        variables.put("motivo", escalation.getTriggerPercentage() == null
                ? accionEnEspanol(escalation.getActionType())
                : "Se alcanzó el %d%% del tiempo del SLA (%s)".formatted(escalation.getTriggerPercentage(),
                        accionEnEspanol(escalation.getActionType()).toLowerCase(ES)));
        String subject = subjectTemplate("ESCALATION", variables,
                "Ticket #%d escalado".formatted(ticket.getNumberTicket()));
        String body = bodyTemplate("ESCALATION", variables,
                "<p>El ticket <b>#%d</b> ha sido <b>escalado</b> (acción: %s).</p>"
                        .formatted(ticket.getNumberTicket(),
                                escaparHtml(accionEnEspanol(escalation.getActionType()))));
        sendNotification(ticket, receiver, "ESCALATION", subject, body);
    }

    public void notifyMonthlyEvaluation(User agent) {
        notifyMonthlyEvaluation(agent, null);
    }

    /**
     * Aviso de evaluación mensual con sus cifras: [mes], [anio], [ticketsCerrados],
     * [sla] (% de cumplimiento), [satisfaccion], [tiempoPromedio] (horas) y
     * [fechaLimite] (fin del mes en curso).
     */
    public void notifyMonthlyEvaluation(User agent, MonthlyEvaluation ev) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("nombre", nameOf(agent));
        variables.put("agente", nameOf(agent));
        LocalDate mes = ev != null && ev.getEvaluationMonth() != null
                ? ev.getEvaluationMonth() : LocalDate.now(ZONA).minusMonths(1);
        String nombreMes = mes.getMonth().getDisplayName(TextStyle.FULL, ES);
        variables.put("mes", nombreMes.substring(0, 1).toUpperCase(ES) + nombreMes.substring(1));
        variables.put("anio", String.valueOf(mes.getYear()));
        variables.put("fechaLimite", LocalDate.now(ZONA).with(TemporalAdjusters.lastDayOfMonth()).format(FECHA));
        variables.put("ticketsCerrados", ev == null || ev.getTicketsClosed() == null ? "0" : String.valueOf(ev.getTicketsClosed()));
        variables.put("sla", ev == null || ev.getSlaComplianceRate() == null ? "0"
                : ev.getSlaComplianceRate().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        variables.put("satisfaccion", ev == null || ev.getSatisfactionScore() == null ? "—"
                : ev.getSatisfactionScore().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString());
        variables.put("tiempoPromedio", ev == null || ev.getAvgResolutionTimeMinutes() == null ? "0"
                : ev.getAvgResolutionTimeMinutes().divide(java.math.BigDecimal.valueOf(60), 1,
                        java.math.RoundingMode.HALF_UP).toPlainString());
        String subject = subjectTemplate("MONTHLY_EVALUATION", variables, "Evaluación mensual disponible");
        String body = bodyTemplate("MONTHLY_EVALUATION", variables,
                "<p>Estimado/a <b>%s</b>, su evaluación mensual está disponible.</p>"
                .formatted(escaparHtml(nameOf(agent))));
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
        String body = "<p>Se le ha asignado la siguiente tarea:</p>"
                + "<p><b>Tarea:</b> " + escaparHtml(task.getTitleTask()) + "<br/>"
                + (task.getDescriptionTask() != null && !task.getDescriptionTask().isBlank()
                        ? "<b>Descripción:</b> " + escaparHtml(task.getDescriptionTask()) + "<br/>" : "")
                + "<b>Fecha:</b> " + fecha + "<br/>"
                + "<b>Hora:</b> " + hora + "<br/>"
                + "<b>Estado:</b> " + task.getStatusTask() + "</p>"
                + "<p>Por favor tome en cuenta la hora acordada y llegue preparado a la cita.</p>";
        Map<String, Object> variables = new HashMap<>();
        variables.put("task", task.getTitleTask());
        variables.put("titulo", task.getTitleTask());
        variables.put("descripcion", task.getDescriptionTask() == null ? "" : textoDelChat(task.getDescriptionTask()));
        variables.put("fecha", fecha);
        variables.put("hora", hora);
        variables.put("estado", task.getStatusTask());
        variables.put("nombre", nameOf(receiver));
        variables.put("agente", nameOf(receiver));
        variables.put("fechaInicio", task.getStartTask() == null ? "Por confirmar" : fechaHora(task.getStartTask()));
        variables.put("fechaFin", task.getDueTask() == null ? "Sin fecha límite" : fechaHora(task.getDueTask()));
        variables.put("duracion", task.getDurationMinutes() == null ? "—" : String.valueOf(task.getDurationMinutes()));
        variables.put("prioridad", "Normal");
        variables.put("asignadaPor", task.getCreatedBy() == null ? quienActua("El sistema") : nameOf(task.getCreatedBy()));
        // Las tareas no son tickets: el botón lleva a Actividades.
        variables.put("urlTicket", urlFrontend("activities"));
        String subjectTpl = subjectTemplate("TASK_ASIGNADA", variables, subject);
        String bodyTpl = bodyTemplate("TASK_ASIGNADA", variables, body);
        sendNotification(null, receiver, "TASK_ASIGNADA", subjectTpl, bodyTpl);
    }

    public void notifyNewAccount(User user, String generatedPassword) {
        Map<String, Object> variables = accountVariables(user, generatedPassword);
        String subject = subjectTemplate("CREACION_USUARIO", variables,
                "Credenciales de acceso - Mesa de Servicios");
        String body = bodyTemplate("CREACION_USUARIO", variables,
                defaultAccountBody("Bienvenido/a a Mesa de Servicios", user, generatedPassword));
        // Urgente: la persona está esperando sus credenciales para entrar.
        mailOutboxService.encolar(user.getCorporateEmailUser(), subject, body, "CREACION_USUARIO",
                MailOutbox.PRIORIDAD_URGENTE, null, null);
    }

    /**
     * Correo de restablecimiento: lleva un enlace, no una contraseña.
     *
* <p>Cambió el contrato a propósito. Antes esta plantilla incluía
     * `[password]` con la clave ya generada, lo que llegaba al correo de cualquiera
     * sin demostrar nada. Ahora el correo solo prueba que se pidió el
     * enlace; la contraseña la elige el titular en el formulario.
     */
    public void notifyPasswordReset(User user, String urlReset) {
        Map<String, Object> variables = new HashMap<>(accountVariables(user, ""));
        variables.put("urlReset", urlReset == null ? "" : urlReset);
        String subject = subjectTemplate("RESET_PASSWORD", variables,
                "Restablecimiento de contraseña - Mesa de Servicios");
        // Las plantillas antiguas mostraban [password], que ya no existe: el
        // restablecimiento va por enlace. Si la plantilla no trae [urlReset] el
        // correo saldría con la contraseña vacía y sin forma de continuar, así que
        // se usa el cuerpo estándar con el botón.
        NotificationTemplate plantilla = activeTemplate("RESET_PASSWORD");
        boolean plantillaConEnlace = plantilla != null && plantilla.getBodyTemplate() != null
                && plantilla.getBodyTemplate().matches("(?s).*(\\[urlReset]|\\{\\{urlReset}}).*");
        String body = plantillaConEnlace
                ? bodyTemplate("RESET_PASSWORD", variables, defaultResetBody(user, urlReset))
                : defaultResetBody(user, urlReset);
        mailOutboxService.encolar(user.getCorporateEmailUser(), subject, body, "RESET_PASSWORD",
                MailOutbox.PRIORIDAD_URGENTE, null, null);
    }

    /**
     * Cuerpo de reserva cuando no hay plantilla guardada para el evento.
     *
     * <p>Se escribe aparte de {@link #defaultAccountBody} porque la estructura es
     * distinta: aquí no hay una contraseña que mostrar, hay un botón. Si se
     * reutilizara el otro, una instalación recién sembrada (sin plantillas
     * propias) seguiría educating al usuario a copiar una contraseña que ya no
     * existe.
     */
    private String defaultResetBody(User user, String urlReset) {
        String boton = urlReset == null || urlReset.isBlank()
                ? ""
                : String.format(
                        "<p><a href=\"%s\" style=\"display:inline-block;background:#062e86;color:#ffffff;"
                                + "text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:bold;\">"
                                + "Establecer mi nueva contraseña</a></p>", urlReset);
        return """
            <div style="background:#f3f4f6;padding:32px 16px;">
              <div style="max-width:620px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;">
                <div style="background:#062e86;padding:24px 32px;text-align:center;">%s</div>
                <div style="padding:28px 36px 12px;font-family:Arial,Helvetica,sans-serif;color:#374151;">
                  <h2 style="color:#062e86;margin:0 0 18px;">Restablecimiento de contraseña</h2>
                  <p>Estimado/a <b>%s</b>:</p>
                  <p>Recibimos una solicitud para restablecer la contraseña de su cuenta.</p>
                  <p>Pulse el botón para establecer una nueva. El enlace es de un solo uso y vence en 24 horas.</p>
                  %s
                  <p>Si usted no solicitó esto, ignore este mensaje: su contraseña actual sigue vigente.</p>
                </div>
              </div>
            </div>
            """.formatted(logoHtml(), nameOf(user), boton);
    }

    private Map<String, Object> accountVariables(User user, String password) {
        return Map.of(
                "nombre", nameOf(user),
                "email", user.getCorporateEmailUser() == null ? "" : user.getCorporateEmailUser(),
                "correo", user.getCorporateEmailUser() == null ? "" : user.getCorporateEmailUser(),
                "password", password == null ? "" : password,
                "clave", password == null ? "" : password,
                // Solo la URL: ver la nota en comunes().
                "logo", brandingLogoUrl());
    }

    private String defaultAccountBody(String heading, User user, String password) {
        return """
            <div style="background:#f3f4f6;padding:32px 16px;">
              <div style="max-width:620px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;">
                <div style="background:#062e86;padding:24px 32px;text-align:center;">%s</div>
                <div style="padding:28px 36px 12px;font-family:Arial,Helvetica,sans-serif;color:#374151;">
                  <h2 style="color:#062e86;margin:0 0 18px;">%s</h2>
                  <p>Estimado/a <b>%s</b>:</p>
                  <p>Su <b>contraseña temporal</b> de acceso es:</p>
                  <div style="margin:18px 0;padding:16px;background:#f3f4f6;border:1px solid #e5e7eb;border-radius:8px;font-size:20px;font-weight:bold;text-align:center;letter-spacing:1px;color:#062e86;">%s</div>
                  <p>Al iniciar sesión deberá cambiarla por una contraseña personalizada.</p>
                </div>
                <div style="background:#062e86;padding:20px 32px;text-align:center;font-family:Arial,Helvetica,sans-serif;">
                  <p style="margin:0;color:#ffffff;font-weight:bold;font-size:14px;">Mesa de Servicios</p>
                  <p style="margin:6px 0 0;color:#c7d2fe;font-size:12px;">Servicio de soporte técnico · COAC Magisterio de Pichincha</p>
                  <p style="margin:6px 0 0;color:#aec0dd;font-size:11px;">© %d COAC Magisterio de Pichincha. Todos los derechos reservados.</p>
                </div>
              </div>
              <p style="max-width:620px;margin:12px auto 0;text-align:center;font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#9ca3af;">Este correo fue generado automáticamente; por favor no responda a este mensaje.</p>
            </div>
            """.formatted(logoHtml(), heading, nameOf(user), password, Year.now().getValue());
    }

    /**
     * Imagen del logo del sistema para la cabecera del correo. Si no hay logo
     * configurado, se muestra el nombre del sistema como texto.
     *
     * <p>Solo se usa concatenada dentro del HTML que arma el propio backend
     * ({@link #wrapEmailBody} y los cuerpos de reserva), nunca como valor de la
     * variable {@code [logo]}: esa entrega la URL a secas, porque todo lo que
     * pasa por el relleno de plantillas se escapa y el HTML llegaría al correo
     * como texto literal.
     */
    private String logoHtml() {
        String url = brandingLogoUrl();
        if (url.isBlank()) {
            return "<span style=\"font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;color:#ffffff;\">Mesa de Servicios</span>";
        }
        return "<img src=\"" + url + "\" alt=\"Mesa de Ayuda COAC\" "
                + "style=\"max-height:60px;max-width:220px;width:auto;height:auto;display:inline-block;\" />";
    }

    /**
     * Envuelve el contenido de cualquier plantilla en el armazón HTML estándar
     * (fondo, cabecera azul con el logo y pie institucional) para que el logo
     * se muestre en todas las plantillas de correo. Si el contenido ya trae su
     * propio armazón (plantillas de cuenta), no se envuelve de nuevo.
     */
    public String wrapEmailBody(String content) {
        if (content == null || content.isBlank()) return content;
        if (content.contains("max-width:620px")) return content;
        String footer = "<div style=\"background:#062e86;padding:20px 32px;text-align:center;font-family:Arial,Helvetica,sans-serif;\">"
                + "<p style=\"margin:0;color:#ffffff;font-weight:bold;font-size:14px;\">Mesa de Servicios</p>"
                + "<p style=\"margin:6px 0 0;color:#c7d2fe;font-size:12px;\">Servicio de soporte técnico · COAC Magisterio de Pichincha</p>"
                + "<p style=\"margin:6px 0 0;color:#aec0dd;font-size:11px;\">© " + Year.now().getValue()
                + " COAC Magisterio de Pichincha. Todos los derechos reservados.</p>"
                + "</div>";
        return "<div style=\"background:#f3f4f6;padding:32px 16px;\">"
                + "<div style=\"max-width:620px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<div style=\"background:#062e86;padding:24px 32px;text-align:center;\">" + logoHtml() + "</div>"
                + "<div style=\"padding:28px 36px 12px;font-family:Arial,Helvetica,sans-serif;color:#374151;\">"
                + "<p style=\"margin:0;\">" + content + "</p>"
                + "</div>"
                + footer
                + "</div>"
                + "<p style=\"max-width:620px;margin:12px auto 0;text-align:center;font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#9ca3af;\">"
                + "Este correo fue generado automáticamente; por favor no responda a este mensaje.</p>"
                + "</div>";
    }

    /**
     * URL absoluta del logo del sistema (app.public-base-url + logo_url).
     */
    private String brandingLogoUrl() {
        String path = brandingConfigRepository.findTopByOrderByIdBrandingAsc()
                .map(BrandingConfig::getLogoUrl).orElse("");
        if (path == null || path.isBlank()) return "";
        if (path.startsWith("http")) return path;
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (path.startsWith("/") ? "" : "/") + path;
    }

    // ============================ NÚCLEO ============================

    private void notifySimple(Ticket ticket, User receiver, String type,
                              String defaultSubject, String title) {
        Map<String, Object> variables = ticket != null ? varsTicket(ticket, receiver) : new HashMap<>();
        if (ticket == null) {
            variables.put("title", title == null ? "" : title);
            variables.put("titulo", title == null ? "" : title);
            variables.put("nombre", nameOf(receiver));
        }
        String subject = subjectTemplate(type, variables, defaultSubject);
        String body = bodyTemplate(type, variables, "Ticket: " + escaparHtml(title == null ? "" : title));
        sendNotification(ticket, receiver, type, subject, body);
    }

    public void sendNotification(Ticket ticket, User receiver, String type,
                                 String subject, String body) {
        if (receiver == null) return;

        Notification notification = new Notification();
        notification.setTicket(ticket);
        notification.setUser(receiver);
        notification.setTypeNotification(type);
        notification.setSubjectNotification(plainText(subject));
        notification.setMessageNotification(resumenEnApp(type, ticket, subject));
        notification.setStatusNotification("PENDING");
        notificationRepository.save(notification);

        // Aviso en vivo al destinatario. Si tiene la app abierta, la campana se
        // actualiza en el momento; si no, la leerá al entrar por la lista.
        sseEventBroker.publishNotification(receiver.getIdUser(), type,
                notification.getSubjectNotification(),
                ticket == null ? null : ticket.getIdTicket());

        if (!isMailConfigured()) {
            // Sin correo no hay nada pendiente de entregar: solo la campana.
            notification.setStatusNotification("SENT");
            notificationRepository.save(notification);
            return;
        }
        // A la cola de correo, en la misma transacción que la notificación. Sale
        // al ritmo que permite el buzón; en un pico, se agrupa por persona.
        for (String email : emailRecipients(ticket, receiver, type)) {
            mailOutboxService.encolar(email, subject, wrapEmailBody(body), type, MailOutbox.PRIORIDAD_NORMAL,
                    notification.getIdNotification(), ticket == null ? null : ticket.getIdTicket());
        }
    }

    /**
     * Envía un correo del sistema con el armazón HTML estándar (logo + pie institucional).
     */
    public void sendSystemEmail(String to, String subject, String content) {
        mailOutboxService.encolar(to, subject, wrapEmailBody(content), "SISTEMA", MailOutbox.PRIORIDAD_NORMAL,
                null, null);
    }

    /**
     * Envío síncrono, usado por el planificador de reintentos, que sí necesita
     * saber si el correo salió para marcar la notificación como enviada o
     * fallida. Ya no se traga la excepción: antes el error se registraba y se
     * devolvía el control, y el llamador marcaba la notificación como SENT
     * aunque el correo nunca se hubiera entregado.
     */
    public boolean sendEmail(String to, String subject, String bodyHtml) {
        try {
            return graphMailSender.enviar(to, subject, bodyHtml);
        } catch (Exception e) {
            log.error("Error enviando email a {}: {}", to, e.getMessage(), e);
            throw new MailDeliveryException("No se pudo enviar el correo a " + to, e);
        }
    }

    /**
     * Resuelve el(los) destinatario(s) de correo según la regla configurada en la
     * plantilla (recipient_rule): SOLICITANTE, AGENTE, AMBOS, CUSTOM o DEFAULT
     * (quien dispara el evento). Las notificaciones internas siempre se guardan
     * para el {@code receiver} original.
     */
    private List<String> emailRecipients(Ticket ticket, User receiver, String type) {
        NotificationTemplate template = activeTemplate(type);
        String rule = template != null && template.getRecipientRule() != null
                && !template.getRecipientRule().isBlank() ? template.getRecipientRule() : "DEFAULT";

        if ("SOLICITANTE".equals(rule)
                && ticket != null && ticket.getUserTicket() != null) {
            return singletonEmail(ticket.getUserTicket());
        }
        if ("AGENTE".equals(rule)
                && ticket != null && ticket.getAgentTicket() != null) {
            return singletonEmail(ticket.getAgentTicket());
        }
        if ("AMBOS".equals(rule) && ticket != null) {
            return emailsOf(ticket.getUserTicket(), ticket.getAgentTicket());
        }
        if ("CUSTOM".equals(rule) && template != null
                && template.getRecipientEmail() != null
                && !template.getRecipientEmail().isBlank()) {
            return List.of(template.getRecipientEmail().trim());
        }
        return singletonEmail(receiver);
    }

    // ============================ PLANTILLAS ============================

    private NotificationTemplate activeTemplate(String name) {
        return templateRepository.findByNameTemplateAndActiveTemplateTrue(name).orElse(null);
    }

    private String subjectTemplate(String name, Map<String, Object> variables, String fallback) {
        NotificationTemplate template = activeTemplate(name);
        if (template == null || isBlank(template.getSubjectTemplate())) {
            return fallback;
        }
        // Un asunto es una sola línea: los saltos que deja fill se cambian por espacios.
        // Y sin entidades: el asunto no es HTML, así que un título con comillas o
        // "&" salía como &quot; o &amp; en la bandeja de entrada.
        return desescapar(fill(template.getSubjectTemplate(), variables, template).replace("<br>", " "));
    }

    private String bodyTemplate(String name, Map<String, Object> variables, String fallback) {
        NotificationTemplate template = activeTemplate(name);
        if (template == null || isBlank(template.getBodyTemplate())) {
            return fallback;
        }
        return fill(template.getBodyTemplate(), variables, template);
    }

    /**
     * Cuerpo y asunto de la plantilla PRUEBA_ENVIO, que edita el SUPER_ADMIN en la
     * sección Plantillas. Si no existe, devuelve null y el envío de prueba usa su
     * propio texto para que la pantalla de Microsoft Graph nunca se quede sin prueba.
     */
    public String[] plantillaPruebaEnvio(String nombre, String email) {
        NotificationTemplate template = activeTemplate("PRUEBA_ENVIO");
        if (template == null || isBlank(template.getBodyTemplate())) {
            return null;
        }
        LocalDateTime ahora = LocalDateTime.now();
        Map<String, Object> variables = new HashMap<>();
        variables.put("nombre", nombre == null || nombre.isBlank() ? "Usuario" : nombre);
        variables.put("email", email == null || email.isBlank() ? "-" : email);
        variables.put("fecha", ahora.toLocalDate().toString());
        variables.put("hora", String.format("%02d:%02d", ahora.getHour(), ahora.getMinute()));
        // La plantilla habla de "servidor SMTP": el envío va por Microsoft Graph,
        // así que se rellenan con los datos reales de esa conexión.
        String remitente = "";
        try {
            remitente = nombre(graphConfigService.getConfig().mailFromAccount());
        } catch (RuntimeException e) {
            log.debug("Sin remitente configurado para la prueba de envío");
        }
        variables.put("fromAddress", remitente);
        variables.put("remitente", remitente);
        variables.put("host", "graph.microsoft.com (Microsoft Graph)");
        variables.put("puerto", "443");
        variables.put("tls", "HTTPS (TLS 1.2 o superior)");
        return new String[] {
                isBlank(template.getSubjectTemplate()) ? "Prueba de envío de correo" : template.getSubjectTemplate(),
                fill(template.getBodyTemplate(), variables, template)
        };
    }

    /**
     * Sustituye las variables [xxx] en una sola pasada.
     *
     * Antes se reemplazaba token por token sobre el texto ya modificado, así que si
     * el valor de una variable contenía el nombre de otra (por ejemplo un título con
     * "[nombre]") el resultado dependía del orden de iteración del mapa, que en los
     * Map.of() es aleatorio en cada arranque. Con una sola pasada el resultado es
     * siempre el mismo.
     *
     * Además se agregan las variables comunes a todos los eventos, para que ninguna
     * plantilla muestre un token literal: el editor ofrece la misma lista para todos
     * los tipos, así que [logo], [fecha], [hora] y [appName] deben funcionar siempre.
     *
     * Se aceptan las dos notaciones porque conviven en las plantillas: las que trae
     * data.sql usan {{id}} y el editor genera [id]. Antes solo se reconocía la
     * segunda, así que las plantillas sembradas llegaban al usuario con los
     * marcadores sin sustituir.
     */
    private String fill(String text, Map<String, Object> variables) {
        return fill(text, variables, null);
    }

    /**
     * Igual que {@link #fill(String, Map)}, pero además conoce la plantilla que
     * se está rellenando, de la que salen las variables de su imagen propia.
     */
    private String fill(String text, Map<String, Object> variables, NotificationTemplate template) {
        if (text == null || text.isEmpty()) return "";

        Map<String, Object> valores = new HashMap<>(comunes(variables));
        valores.putAll(variables);
        // El editor ofrece indistintamente el español o el inglés; si un evento solo
        // define uno, el otro se rellena con el mismo valor en vez de quedar vacío.
        alias(valores, "titulo", "title");
        alias(valores, "estado", "status");
        alias(valores, "prioridad", "priority");
        alias(valores, "email", "correo");
        alias(valores, "respuesta", "mensaje");
        alias(valores, "password", "clave");
        alias(valores, "nombre", "agente");
        imagenDePlantilla(valores, template);

Matcher matcher = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}|\\[([a-zA-Z0-9_]+)]").matcher(text);
          StringBuilder out = new StringBuilder();
          int fin = 0;
          while (matcher.find()) {
              out.append(text, fin, matcher.start());
              // El grupo 1 corresponde a {{var}} y el 2 a [var]; el que venga null es
              // la notación que no se usó en esa coincidencia.
              String clave = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
              Object valor = valores.get(clave);
              out.append(valor == null ? "" : conSaltos(String.valueOf(valor)));
              fin = matcher.end();
          }
          out.append(text.substring(fin));
          return out.toString();
      }

  /**
   * Escapa un valor antes de insertarlo en el cuerpo del correo.
   *
   * <p>El cuerpo se envia como HTML (Microsoft Graph, contentType HTML) y los
   * valores vienen de datos que escribe cualquier solicitante: el titulo y la
   * descripcion de un ticket, el texto de una respuesta. Sin escapar, un titulo
   * con {@code <img src=x onerror=...>} llegaba renderizado a los agentes y a la
   * persona que lo reporto.
   *
   * <p>Los clientes de correo no ejecutan {@code <script>}, pero si renderizan
   * {@code onerror}, {@code svg onload} y enlaces, asi que el resultado es
   * phishing con remitente institucional y capacidad de redirigir. Es el mismo
   * motivo por el que las imagenes de plantilla rechazan los SVG.
   *
   * <p>Solo afecta al correo: las notificaciones internas de la UI pasan por
   * {@link #plainText}, que deshace estas entidades al final, de modo que alli el
   * texto se sigue viendo igual. El resumen corto de la campana se arma aparte y
   * no pasa por aqui.
   */
  /**
   * Texto legible a partir del HTML del editor del chat: los saltos de párrafo y
   * de línea quedan como saltos, el resto de etiquetas se quitan y las entidades
   * se decodifican. El resultado es texto plano; al ir al correo se escapa.
   */
  static String textoDelChat(String html) {
      if (html == null || html.isBlank()) {
          return "";
      }
      String t = html
              .replaceAll("(?i)<br\\s*/?>", "\n")
              .replaceAll("(?i)</(p|div|li|h[1-6]|blockquote|tr)>", "\n")
              .replaceAll("(?i)<li[^>]*>", "• ")
              .replaceAll("<[^>]*>", "")
              .replace("&nbsp;", " ")
              .replace("&lt;", "<")
              .replace("&gt;", ">")
              .replace("&quot;", "\"")
              .replace("&#39;", "'")
              .replace("&amp;", "&");
      return t.replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
  }

  /** Deshace el escape de {@link #escaparHtml} (para el asunto, que es texto plano). */
  private static String desescapar(String texto) {
      return texto.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
              .replace("&#39;", "'").replace("&amp;", "&");
  }

  /** Escapa el texto y conserva sus saltos de línea como {@code <br>}. */
  private static String conSaltos(String texto) {
      return texto == null ? "" : escaparHtml(texto).replace("\r\n", "\n").replace("\n", "<br>");
  }

  private static String escaparHtml(String valor) {
      // Se escapan solo los cinco caracteres con significado en HTML en vez de
      // usar escapeHtml4 de commons-text. La diferencia no es academica: esta
      // institucion escribe en español y escapeHtml4 convierte cada tilde en su
      // entidad (&eacute;, &oacute;, &ntilde;), de modo que un correo entero
      // acababa siendo sopa de entidades: mas pesado y bastante menos legible
      // para quien lo revisa en texto plano o lo filtra en el servidor de correo.
      StringBuilder out = new StringBuilder(valor.length() + 16);
      for (int i = 0; i < valor.length(); i++) {
          char c = valor.charAt(i);
          switch (c) {
              case '&' -> out.append("&amp;");
              case '<' -> out.append("&lt;");
              case '>' -> out.append("&gt;");
              case '"' -> out.append("&quot;");
              case '\'' -> out.append("&#39;");
              default -> out.append(c);
          }
      }
      return out.toString();
  }

    /**
 * Variables de la imagen que el administrador subió a la plantilla.
 * <p>
 * Solo se ofrece [imagenUrl], con la dirección a secas: el que escribe el HTML
 * es el administrador y decide él la etiqueta, el ancho y los estilos. Se
 * inyecta la URL absoluta porque el correo se abre fuera del navegador y una
 * ruta relativa no cargaria nada. Con la plantilla sin imagen, la variable queda
 * vacia en vez de dejar el token a la vista en el correo.
 */
private void imagenDePlantilla(Map<String, Object> valores, NotificationTemplate template) {
        if (template == null) {
            return;
        }
        String url = templateImageUrl(template.getImageUrl());
        valores.put("imagenUrl", url);
        valores.put("imageUrl", url);
    }

    /** URL absoluta de la imagen de la plantilla, para que la cargue el correo. */
    private String templateImageUrl(String path) {
        if (path == null || path.isBlank()) return "";
        String limpio = path.trim();
        if (limpio.startsWith("http")) return limpio;
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) return "";
        return base + (limpio.startsWith("/") ? "" : "/") + limpio;
    }

    /** Copia el valor de "origen" a "destino" solo si el destino está vacío. */
    private void alias(Map<String, Object> valores, String destino, String origen) {
        boolean destinoVacio = valores.get(destino) == null || String.valueOf(valores.get(destino)).isEmpty();
        Object valor = valores.get(origen);
        if (destinoVacio && valor != null && !String.valueOf(valor).isEmpty()) {
            valores.put(destino, valor);
        }
    }

    /** Variables disponibles en cualquier plantilla, se refuerzan con las del evento. */
    private Map<String, Object> comunes(Map<String, Object> variables) {
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        Map<String, Object> base = new HashMap<>();
        base.put("appName", "Mesa de Servicios");
        base.put("empresa", "COAC Magisterio de Pichincha");
        base.put("compania", "COAC Magisterio de Pichincha");
        base.put("fecha", ahora.toLocalDate().format(FECHA));
        base.put("hora", String.format("%02d:%02d", ahora.getHour(), ahora.getMinute()));
        // Solo la URL, no la etiqueta <img>. El relleno escapa todo lo que
        // inserta, asi que antes el HTML del logo llegaba al correo como texto
        // literal (&lt;img src=&quot;...) y el encabezado se veía roto.
        // Quien escribe la plantilla pone la etiqueta, igual que con [imagenUrl].
        base.put("logo", brandingLogoUrl());
        // Enlaces de la SPA. Se calculan aquí y no en cada disparador porque
        // cualquier plantilla puede usarlos, incluidas las que no son de ticket.
        String baseFrontend = urlFrontend("");
        base.put("sitioWeb", baseFrontend);
        base.put("soporte", baseFrontend);
        base.put("urlDashboard", urlFrontend("dashboard"));
        base.put("urlLogin", urlFrontend("auth/login"));
        base.put("urlNuevoTicket", urlFrontend("tickets/new"));
        base.put("urlPassword", urlFrontend("settings/password"));
        base.put("urlSlaConfig", urlFrontend("admin/sla-config"));
        String urlTicket = urlTicketDelEvento(variables);
        base.put("urlTicket", urlTicket);
        base.put("urlCalificar", urlTicket.isEmpty() ? "" : urlTicket + "?calificar=1");
        // La bandera de "sin valor" permite que una variable propia del evento
        // (por ejemplo [nombre]) gane sobre la vacía del conjunto común.
        if (!variables.containsKey("nombre")) {
            base.put("nombre", "");
        }
        for (String alias : new String[] { "titulo", "estado", "prioridad", "agente", "email",
                "respuesta", "percentage", "password", "task", "descripcion", "action" }) {
            base.put(alias, "");
        }
        return base;
    }

    /**
     * Une la base del frontend con una ruta, sin duplicar la barra final. Si
     * {@code app.frontend-base-url} no está definida devuelve cadena vacía: es
     * preferible que el botón salga sin destino antes que apuntar a la API, que
     * no es navegable para el usuario que recibe el correo.
     */
    private String urlFrontend(String ruta) {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return "";
        }
        String sufijo = ruta == null ? "" : ruta.trim();
        while (sufijo.startsWith("/")) {
            sufijo = sufijo.substring(1);
        }
        return sufijo.isEmpty() ? base : base + "/" + sufijo;
    }

    /** Enlace al ticket del evento, o vacío si el evento no va sobre un ticket. */
    private String urlTicketDelEvento(Map<String, Object> variables) {
        // El enlace usa el uuid (ticketId); [id] es el número para mostrar.
        Object id = variables.get("ticketId");
        String ticketId = id == null ? "" : String.valueOf(id).trim();
        if (ticketId.isEmpty() || "0".equals(ticketId)) {
            return "";
        }
        return urlFrontend("tickets/" + ticketId);
    }

    /**
     * Número visible del ticket para las variables de plantilla. Map.of no admite
     * nulos: si el ticket aún no trae su número (recién creado sin releer), queda
     * vacío en vez de romper el envío.
     */
    private static Object numeroDe(Ticket ticket) {
        return ticket == null || ticket.getNumberTicket() == null ? "" : ticket.getNumberTicket();
    }

    // ============================ UTILIDADES ============================

    private boolean isMailConfigured() {
        try {
            MicrosoftGraphConfigResponse config = graphConfigService.getConfig();
            if (!config.enabled() || !config.mailEnabled()) {
                return false;
            }
            return config.mailFromAccount() != null && !config.mailFromAccount().isBlank();
        } catch (Exception e) {
            log.warn("No se pudo leer la configuración de correo de Graph: {}", e.getMessage());
            return false;
        }
    }

/**
 * Nombre de la persona destinataria.
 *
 * <p>Antes devolvía el correo de la empresa, así que los correos decían
 * "Estimado/a <b>usuario@coacmagisterio.fin.ec</b>". Ahora usa el mismo criterio
 * que el resto de la aplicación: el nombre de Outlook y, si no hay, los nombres
 * o la parte del correo anterior a la arroba.
 */
private String nameOf(User user) {
    return NombreVisible.de(user);
}

    private List<String> singletonEmail(User user) {
        if (user == null || isBlank(user.getCorporateEmailUser())) {
            return List.of();
        }
        return List.of(user.getCorporateEmailUser().trim());
    }

    private List<String> emailsOf(User... users) {
        Set<String> emails = new LinkedHashSet<>();
        for (User user : users) {
            if (user != null && !isBlank(user.getCorporateEmailUser())) {
                emails.add(user.getCorporateEmailUser().trim());
            }
        }
        return new ArrayList<>(emails);
    }

    /**
     * Resumen de una línea para la notificación de la campana.
     *
     * Antes se guardaba {@code plainText(body)}, y como {@code body} es el
     * correo entero, la notificación interna acababa con los 1.500 caracteres
     * del correo (cartela, pasos, pie y aviso legal) en el desplegable y en la
     * página de notificaciones. Se guardaba en texto plano, sí, pero no en un
     * texto que una persona quiera leer en una lista.
     *
     * Si el tipo no está en la tabla se cae al asunto, que siempre es corto.
     */
    private String resumenEnApp(String type, Ticket ticket, String subject) {
        String formato = RESUMENES_EN_APP.get(type == null ? "" : type);
        if (formato == null) {
            return plainText(subject);
        }
        String id = ticket == null || ticket.getNumberTicket() == null
                ? "" : String.valueOf(ticket.getNumberTicket());
        String titulo = ticket == null || ticket.getTitleTicket() == null
                ? "" : ticket.getTitleTicket().trim();
        return formato
                .replace("{id}", id)
                .replace("{titulo}", titulo.isEmpty() ? "" : " \"" + titulo + "\"");
    }

    /**
     * Texto corto de la notificación interna por tipo de evento. Los marcadores
     * son {@code {id}} y {@code {titulo}}, no los de las plantillas de correo:
     * aquí no se pasan por {@code fill()} porque solo hay dos valores.
     */
    private static final Map<String, String> RESUMENES_EN_APP = Map.ofEntries(
            Map.entry("TICKET_CREADO", "Se registró tu ticket {id}{titulo}."),
            Map.entry("TICKET_ASIGNADO", "Tu ticket {id}{titulo} fue asignado."),
            Map.entry("TICKET_ACTUALIZADO", "Tu ticket {id}{titulo} tuvo cambios."),
            Map.entry("TICKET_RESUELTO", "Tu ticket {id}{titulo} fue resuelto."),
            Map.entry("TICKET_CERRADO", "Tu ticket {id}{titulo} fue cerrado."),
            Map.entry("ESCALATION", "Tu ticket {id}{titulo} fue escalado."),
            Map.entry("SLA_WARNING", "El ticket {id}{titulo} está por incumplir su SLA."),
            Map.entry("SLA_BREACH", "El ticket {id}{titulo} incumplió su SLA."),
            Map.entry("SLA_BREACHED", "El ticket {id}{titulo} incumplió su SLA."),
            Map.entry("RATING_REQUEST", "Califica la atención de tu ticket {id}{titulo}."),
            Map.entry("MONTHLY_EVALUATION", "Tu evaluación mensual ya está disponible."),
            Map.entry("TASK_ASIGNADA", "Se te asignó una tarea."));

    /** Convierte el HTML (o texto) a texto plano para las notificaciones internas de la UI. */
    private String plainText(String html) {
        if (html == null) return "";
        String text = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p\\s*>", "\n")
                .replaceAll("(?is)</div\\s*>", "\n")
                .replaceAll("(?is)</tr\\s*>", "\n")
                .replaceAll("(?is)</h[1-6]\\s*>", "\n")
                .replaceAll("(?is)<li\\s*>", "- ")
                .replaceAll("(?is)<[^>]+>", "");
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .strip();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}