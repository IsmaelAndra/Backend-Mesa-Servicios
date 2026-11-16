package com.mesaayuda.service;

import com.mesaayuda.dto.AccesoConfidencialResponse;
import com.mesaayuda.entity.AccesoConfidencial;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.AccesoConfidencialRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Acceso de emergencia a un ticket confidencial.
 *
 * <p>El súper admin y el auditor no ven los confidenciales. Si hace falta (una
 * queja, una investigación), piden acceso con un motivo; el jefe del área que
 * lo atiende lo aprueba o lo rechaza. Aprobado vale {@link #HORAS_ACCESO} horas,
 * es de solo lectura y cada apertura del ticket queda en su auditoría.
 */
@Service
public class AccesoConfidencialService {

    private static final Logger log = LoggerFactory.getLogger(AccesoConfidencialService.class);

    /** Duración de un acceso aprobado. */
    public static final int HORAS_ACCESO = 2;
    private static final String TIPO_AVISO = "ACCESO_CONFIDENCIAL";

    private final AccesoConfidencialRepository repo;
    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final TicketAccessPolicy accessPolicy;
    private final JefaturaService jefaturaService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SystemAuditService systemAuditService;

    public AccesoConfidencialService(AccesoConfidencialRepository repo, TicketRepository ticketRepository,
                                     UserService userService, TicketAccessPolicy accessPolicy,
                                     JefaturaService jefaturaService, NotificationService notificationService,
                                     AuditService auditService, SystemAuditService systemAuditService) {
        this.repo = repo;
        this.ticketRepository = ticketRepository;
        this.userService = userService;
        this.accessPolicy = accessPolicy;
        this.jefaturaService = jefaturaService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.systemAuditService = systemAuditService;
    }

    @Transactional
    public AccesoConfidencialResponse solicitar(UUID ticketId, String motivo) {
        User actual = userService.getCurrentUser();
        if (actual.getRolUser() == null || !Roles.veTodo(actual.getRolUser().getNameRole())) {
            throw new AccessDeniedException("Solo el súper administrador o el auditor piden acceso de emergencia");
        }
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 10) {
            throw new BadRequestException("Explique el motivo (al menos 10 caracteres): lo lee el jefe que decide");
        }
        Ticket ticket = ticket(ticketId);
        if (!TicketAccessPolicy.esConfidencial(ticket)) {
            throw new BadRequestException("El ticket no es confidencial");
        }
        if (accessPolicy.puedeLeer(ticket, actual)) {
            throw new BadRequestException("Ya tiene acceso a este ticket");
        }
        AccesoConfidencial previa = ultima(ticketId, actual.getIdUser());
        if (previa != null && AccesoConfidencial.PENDIENTE.equals(previa.getEstado())) {
            return dto(previa);
        }
        AccesoConfidencial a = new AccesoConfidencial();
        a.setTicket(ticket);
        a.setSolicitante(actual);
        a.setMotivo(razon.length() > 500 ? razon.substring(0, 500) : razon);
        a = repo.save(a);

        auditService.record(ticket, "CONFIDENTIAL_ACCESS_REQUEST", null, actual.getCorporateEmailUser(), a.getMotivo());
        systemAuditService.record("ACCESO_CONFIDENCIAL_SOLICITADO", "Ticket", ticket.getIdTicket(),
                actual.getCorporateEmailUser(), a.getMotivo());
        String asunto = "Solicitud de acceso al ticket confidencial #" + ticket.getNumberTicket();
        String cuerpo = "<p>" + escapar(actual.getCorporateEmailUser()) + " pide acceso de solo lectura al ticket "
                + "confidencial #" + ticket.getNumberTicket() + ".</p><p><b>Motivo:</b> " + escapar(a.getMotivo())
                + "</p><p>Ábralo en la mesa de servicios para aprobar o rechazar.</p>";
        for (User jefe : jefaturaService.jefesDe(ticket.getAreaDestinoTicket().getIdArea())) {
            notificationService.notifyConPlantilla(ticket, jefe, TIPO_AVISO, asunto, cuerpo, null);
        }
        log.warn("{} solicitó acceso al ticket confidencial #{}", actual.getCorporateEmailUser(), ticket.getNumberTicket());
        return dto(a);
    }

    /** Última solicitud de la persona sobre el ticket (null si nunca pidió). */
    @Transactional(readOnly = true)
    public AccesoConfidencialResponse miSolicitud(UUID ticketId) {
        User actual = userService.getCurrentUser();
        AccesoConfidencial a = ultima(ticketId, actual.getIdUser());
        return a == null ? null : dto(a);
    }

    /** Pendientes que decide el jefe: las de los tickets que atienden sus áreas. */
    @Transactional(readOnly = true)
    public List<AccesoConfidencialResponse> pendientesParaMi() {
        Set<UUID> areas = jefaturaService.areasACargo(userService.getCurrentUser());
        if (areas.isEmpty()) {
            return List.of();
        }
        return repo.pendientesDeAreas(areas).stream().map(this::dto).toList();
    }

    @Transactional
    public AccesoConfidencialResponse decidir(UUID accesoId, boolean aprobar, String comentario) {
        User actual = userService.getCurrentUser();
        AccesoConfidencial a = repo.findById(accesoId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de acceso", "id", accesoId));
        Ticket ticket = a.getTicket();
        if (!jefaturaService.esJefeDe(actual, ticket.getAreaDestinoTicket().getIdArea())) {
            throw new AccessDeniedException("Solo el jefe del área que atiende el ticket decide el acceso");
        }
        if (!AccesoConfidencial.PENDIENTE.equals(a.getEstado())) {
            throw new BadRequestException("La solicitud ya fue decidida");
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        a.setEstado(aprobar ? AccesoConfidencial.APROBADO : AccesoConfidencial.RECHAZADO);
        a.setDecididoPor(actual);
        a.setDecididoAt(ahora);
        a.setComentario(comentario == null || comentario.isBlank() ? null : comentario.trim());
        a.setVenceAt(aprobar ? ahora.plusHours(HORAS_ACCESO) : null);
        a = repo.save(a);

        String accion = aprobar ? "CONFIDENTIAL_ACCESS_APPROVED" : "CONFIDENTIAL_ACCESS_REJECTED";
        auditService.record(ticket, accion, a.getSolicitante().getCorporateEmailUser(), actual.getCorporateEmailUser(),
                a.getComentario());
        systemAuditService.record(aprobar ? "ACCESO_CONFIDENCIAL_APROBADO" : "ACCESO_CONFIDENCIAL_RECHAZADO",
                "Ticket", ticket.getIdTicket(), actual.getCorporateEmailUser(),
                "Solicitante: " + a.getSolicitante().getCorporateEmailUser());
        String asunto = aprobar
                ? "Acceso aprobado al ticket confidencial #" + ticket.getNumberTicket() + " por " + HORAS_ACCESO + " horas"
                : "Acceso rechazado al ticket confidencial #" + ticket.getNumberTicket();
        String cuerpo = "<p>" + escapar(asunto) + ".</p>"
                + (a.getComentario() == null ? "" : "<p><b>Comentario:</b> " + escapar(a.getComentario()) + "</p>");
        notificationService.notifyConPlantilla(ticket, a.getSolicitante(), TIPO_AVISO, asunto, cuerpo, null);
        return dto(a);
    }

    private Ticket ticket(UUID id) {
        return ticketRepository.findById(id)
                .filter(t -> !Boolean.TRUE.equals(t.getDeletedTicket()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }

    private AccesoConfidencial ultima(UUID ticketId, UUID userId) {
        return repo.findFirstByTicketIdTicketAndSolicitanteIdUserOrderByCreatedAtAccesoDesc(ticketId, userId)
                .orElse(null);
    }

    private AccesoConfidencialResponse dto(AccesoConfidencial a) {
        // Un aprobado vencido se muestra como tal: hay que volver a pedirlo.
        String estado = AccesoConfidencial.APROBADO.equals(a.getEstado()) && !a.vigente(OffsetDateTime.now())
                ? "VENCIDO" : a.getEstado();
        return new AccesoConfidencialResponse(a.getIdAcceso(), a.getTicket().getIdTicket(),
                a.getTicket().getNumberTicket(), a.getSolicitante().getCorporateEmailUser(), a.getMotivo(), estado,
                a.getComentario(), a.getCreatedAtAcceso(), a.getVenceAt());
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
