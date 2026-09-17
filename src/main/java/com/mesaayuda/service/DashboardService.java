package com.mesaayuda.service;

import com.mesaayuda.dto.DashboardStats;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.CategoryRepository;
import com.mesaayuda.repository.PriorityRepository;
import com.mesaayuda.repository.StatusRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private StatusRepository statusRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PriorityRepository priorityRepository;

    @Autowired
    private TicketSlaRepository ticketSlaRepository;

    @Autowired
    private UserService userService;

    public DashboardStats getStats(String statusFilter, String areaFilter, String categoryFilter,
                                   LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().minusYears(1);
        OffsetDateTime end = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().plusDays(1);

        com.mesaayuda.entity.User current = userService.getCurrentUser();
        String role = current.getRolUser().getNameRole();
        Long currentId = current.getIdUser();

        List<Ticket> tickets = switch (role) {
            case "USER" -> ticketRepository.findOpenTicketsByUser(currentId);
            case "AGENT" -> ticketRepository.findOpenTicketsByAgent(currentId);
            default -> ticketRepository.findOpenTickets();
        };

        List<Ticket> filtered = tickets.stream()
                .filter(t -> !t.getCreatedAtTicket().isBefore(start) && t.getCreatedAtTicket().isBefore(end))
                .filter(t -> statusFilter == null || t.getStatusTicket().getNameStatus().equalsIgnoreCase(statusFilter))
                .filter(t -> areaFilter == null || t.getAreaTicket().getNameArea().equalsIgnoreCase(areaFilter))
                .filter(t -> categoryFilter == null || t.getCategoryTicket().getNameCategory().equalsIgnoreCase(categoryFilter))
                .toList();

        long total = switch (role) {
            case "USER" -> ticketRepository.countCreatedBetweenByUser(start, end, currentId);
            case "AGENT" -> ticketRepository.countCreatedBetweenByAgent(start, end, currentId);
            default -> ticketRepository.countCreatedBetween(start, end);
        };
        long open = filtered.stream()
                .filter(t -> !"CERRADO".equals(t.getStatusTicket().getNameStatus()))
                .count();
        long resolved = switch (role) {
            case "USER" -> ticketRepository.countResolvedBetweenByUser(start, end, currentId);
            case "AGENT" -> ticketRepository.countResolvedBetweenByAgent(start, end, currentId);
            default -> ticketRepository.countResolvedBetween(start, end);
        };
        long critical = filtered.stream()
                .filter(t -> "CRITICA".equals(t.getPriorityTicket().getNamePriority())
                        && !"CERRADO".equals(t.getStatusTicket().getNameStatus()))
                .count();

        // SLA compliance (tickets resueltos y no incumplidos)
        double slaOk = filtered.stream()
                .filter(t -> "RESUELTO".equals(t.getStatusTicket().getNameStatus())
                        || "CERRADO".equals(t.getStatusTicket().getNameStatus()))
                .filter(t -> ticketSlaRepository.findByTicketIdTicket(t.getIdTicket())
                        .map(ts -> !Boolean.TRUE.equals(ts.getResolutionBreached()))
                        .orElse(true))
                .count();
        double slaTotal = filtered.stream()
                .filter(t -> "RESUELTO".equals(t.getStatusTicket().getNameStatus())
                        || "CERRADO".equals(t.getStatusTicket().getNameStatus()))
                .count();
        Double slaCompliance = slaTotal == 0 ? null : Math.round(slaOk / slaTotal * 10000) / 100.0;

        // Tiempo promedio de resolución (horas)
        List<Duration> durations = filtered.stream()
                .filter(t -> t.getClosingDateTicket() != null)
                .map(t -> Duration.between(t.getCreatedAtTicket(), t.getClosingDateTicket()))
                .toList();
        Double avgResolutionHours = durations.isEmpty() ? null
                : Math.round(durations.stream().mapToLong(Duration::toMinutes)
                        .average().orElse(0) / 60.0 * 100) / 100.0;

        // Alertas SLA sobre tickets abiertos
        OffsetDateTime riskLimit = OffsetDateTime.now().plusHours(2);
        long slaBreachedOpen = 0;
        long slaAtRisk = 0;
        for (Ticket t : filtered) {
            if ("CERRADO".equals(t.getStatusTicket().getNameStatus())
                    || "RESUELTO".equals(t.getStatusTicket().getNameStatus())) {
                continue;
            }
            TicketSla ts = ticketSlaRepository.findByTicketIdTicket(t.getIdTicket()).orElse(null);
            if (ts == null) {
                continue;
            }
            if (Boolean.TRUE.equals(ts.getResolutionBreached())) {
                slaBreachedOpen++;
            } else if (ts.getResolutionDueAt() != null
                    && !ts.getResolutionDueAt().isBefore(OffsetDateTime.now())
                    && ts.getResolutionDueAt().isBefore(riskLimit)) {
                slaAtRisk++;
            }
        }

        Map<String, Long> byStatus = tickets.stream()
                .filter(t -> !t.getCreatedAtTicket().isBefore(start) && t.getCreatedAtTicket().isBefore(end))
                .collect(Collectors.groupingBy(t -> t.getStatusTicket().getNameStatus(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byArea = tickets.stream()
                .filter(t -> !t.getCreatedAtTicket().isBefore(start) && t.getCreatedAtTicket().isBefore(end))
                .collect(Collectors.groupingBy(t -> t.getAreaTicket().getNameArea(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byCategory = tickets.stream()
                .filter(t -> !t.getCreatedAtTicket().isBefore(start) && t.getCreatedAtTicket().isBefore(end))
                .collect(Collectors.groupingBy(t -> t.getCategoryTicket().getNameCategory(),
                        LinkedHashMap::new, Collectors.counting()));

        List<Integer> last12Weeks = new java.util.ArrayList<>();
        OffsetDateTime weekStart = OffsetDateTime.now().with(java.time.DayOfWeek.MONDAY)
                .toLocalDate().minusWeeks(11).atStartOfDay().atOffset(ZoneOffset.UTC);
        for (int i = 0; i < 12; i++) {
            OffsetDateTime weekFrom = weekStart.plusWeeks(i);
            OffsetDateTime weekTo = weekFrom.plusWeeks(1);
            List<Ticket> source = tickets;
            int count = (int) source.stream()
                    .filter(t -> !t.getCreatedAtTicket().isBefore(weekFrom) && t.getCreatedAtTicket().isBefore(weekTo))
                    .count();
            last12Weeks.add(count);
        }

        return new DashboardStats(total, open, resolved, critical, slaCompliance,
                avgResolutionHours, slaBreachedOpen, slaAtRisk,
                byStatus, byArea, byCategory, last12Weeks);
    }
}