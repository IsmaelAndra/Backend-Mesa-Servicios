package com.mesaayuda.service;

import com.mesaayuda.dto.SolicitanteResumenResponse;
import com.mesaayuda.dto.UserResponse;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketRating;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.mapper.UserMapper;
import com.mesaayuda.repository.TicketRatingRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SolicitanteService {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketRatingRepository ticketRatingRepository;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private UserService userService;

    private static final Logger log = LoggerFactory.getLogger(SolicitanteService.class);

    /**
     * Ficha 360° de un solicitante: perfil, historial de tickets,
     * métricas de satisfacción y resolución, distribución por estado
     * y tendencia de los últimos 6 meses.
     */
    /**
     * Ficha 360° de un solicitante: sus tickets, calificaciones y estadísticas.
     * Antes el endpoint aceptaba cualquier id sin comprobar nada, así que
     * cualquier cuenta con sesión iniciada podía pedir la ficha de otra persona
     * y ver su histórico de tickets y sus calificaciones. Ahora solo la propia
     * persona o el personal.
     */
    @Transactional(readOnly = true)
    public SolicitanteResumenResponse resumen(UUID userId) {
        User actual = userService.getCurrentUser();
        boolean esPersonal = actual != null && actual.getRolUser() != null
                && List.of("AGENT", "ADMIN", "SUPER_ADMIN")
                .contains(actual.getRolUser().getNameRole());

        if (!esPersonal && !actual.getIdUser().equals(userId)) {
            log.warn("Acceso denegado: {} intentó consultar la ficha 360° del usuario {}.",
                    actual.getCorporateEmailUser(), userId);
            throw new AccessDeniedException("No tiene acceso a la ficha de este solicitante");
        }
        return construirResumen(userId);
    }

    private SolicitanteResumenResponse construirResumen(UUID userId) {
        User solicitante = userRepository.findById(userId)
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", userId));

        List<Ticket> tickets = ticketRepository
                .findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(userId);

        Long totalTickets = (long) tickets.size();
        Long openTickets = tickets.stream()
                .filter(t -> t.getStatusTicket() != null
                        && !"CERRADO".equals(t.getStatusTicket().getNameStatus()))
                .count();
        Long resolvedTickets = tickets.stream()
                .filter(t -> t.getStatusTicket() != null
                        && ("RESUELTO".equals(t.getStatusTicket().getNameStatus())
                        || "CERRADO".equals(t.getStatusTicket().getNameStatus())))
                .count();

        Double avgSatisfaction = null;
        double sumaSatisfaccion = 0;
        int nCalificaciones = 0;
        for (TicketRating r : ticketRatingRepository.findByRequester(userId)) {
            Integer v = r.getOverallSatisfaction();
            if (v != null) {
                sumaSatisfaccion += v;
                nCalificaciones++;
            }
        }
        if (nCalificaciones > 0) {
            avgSatisfaction = Math.round(sumaSatisfaccion / nCalificaciones * 100.0) / 100.0;
        }

        Double avgResolutionHours = null;
        long totalMinutos = 0;
        int nResueltos = 0;
        for (Ticket t : tickets) {
            if (t.getCreatedAtTicket() != null && t.getClosingDateTicket() != null) {
                totalMinutos += Duration.between(t.getCreatedAtTicket(), t.getClosingDateTicket()).toMinutes();
                nResueltos++;
            }
        }
        if (nResueltos > 0) {
            avgResolutionHours = Math.round(totalMinutos / (double) nResueltos / 60.0 * 100.0) / 100.0;
        }

        Map<String, Long> ticketsPorEstado = new LinkedHashMap<>();
        for (Ticket t : tickets) {
            String estado = t.getStatusTicket() != null
                    ? t.getStatusTicket().getNameStatus() : "SIN ESTADO";
            ticketsPorEstado.merge(estado, 1L, Long::sum);
        }

        List<Long> ticketsUltimos6Meses = new ArrayList<>();
        LocalDate base = LocalDate.now(ZONE).withDayOfMonth(1).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            LocalDate desde = base.plusMonths(i);
            LocalDate hasta = desde.plusMonths(1);
            long count = tickets.stream()
                    .filter(t -> t.getCreatedAtTicket() != null)
                    .filter(t -> {
                        LocalDate fecha = t.getCreatedAtTicket().toLocalDate();
                        return !fecha.isBefore(desde) && fecha.isBefore(hasta);
                    })
                    .count();
            ticketsUltimos6Meses.add(count);
        }

        UserResponse solicitanteResp = userMapper.toResponse(solicitante);
        if (solicitanteResp != null) {
            solicitanteResp = solicitanteResp.withSessionState(presenceService.estadoDe(solicitante.getIdUser()));
        }
        return new SolicitanteResumenResponse(
                solicitanteResp,
                tickets.stream().limit(12).map(ticketMapper::toResponse).toList(),
                totalTickets, openTickets, resolvedTickets,
                avgSatisfaction, avgResolutionHours,
                ticketsPorEstado, ticketsUltimos6Meses);
    }
}