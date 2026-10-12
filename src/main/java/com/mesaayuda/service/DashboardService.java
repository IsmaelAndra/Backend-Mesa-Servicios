package com.mesaayuda.service;

import com.mesaayuda.dto.DashboardOperations;
import com.mesaayuda.dto.DashboardStats;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketRating;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.CategoryRepository;
import com.mesaayuda.repository.PriorityRepository;
import com.mesaayuda.repository.StatusRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketRatingRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

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
    private TicketRatingRepository ticketRatingRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private SseEventBroker sseEventBroker;

    /** Estados que ya no cuentan como trabajo pendiente. */
    private static final Set<String> TERMINADOS = Set.of("CERRADO", "RESUELTO");

    /** Lotes del IN de las consultas por id: PostgreSQL no admite listas ilimitadas. */
    private static final int LOTE = 1000;

    private static final DateTimeFormatter ETIQUETA_SEMANA =
            DateTimeFormatter.ofPattern("d MMM", new Locale("es", "EC"));

    public DashboardStats getStats(String statusFilter, String areaFilter, String categoryFilter,
                                   LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().minusYears(1);
        OffsetDateTime end = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now().plusDays(1);
        // Periodo anterior de la misma duración, para la tendencia de los indicadores.
        Duration duracion = Duration.between(start, end);
        OffsetDateTime prevStart = start.minus(duracion);

        com.mesaayuda.entity.User current = userService.getCurrentUser();
        String role = current.getRolUser().getNameRole();
        UUID currentId = current.getIdUser();

        // Una sola consulta para los dos periodos: se separan en memoria. Así el
        // alcance del rol se aplica una vez y ambos periodos salen de la misma regla.
        List<Ticket> ampliada = switch (role) {
            case "USER" -> ticketRepository.findTicketsBetweenByUser(prevStart, end, currentId);
            case "AGENT" -> ticketRepository.findTicketsBetweenByAgent(prevStart, end, currentId);
            default -> ticketRepository.findTicketsBetween(prevStart, end);
        };

        // Poblacion del alcance del rol en el rango pedido, incluidos los CERRADO.
        // Todo el tablero (total, resueltos y graficos) se calcula sobre esta
        // misma lista: si el total usara una consulta y los graficos otra, un
        // agente veria cifras que no cuadran entre si.
        List<Ticket> population = ampliada.stream()
                .filter(t -> t.getCreatedAtTicket() == null || !t.getCreatedAtTicket().isBefore(start))
                .toList();
        List<Ticket> anterior = ampliada.stream()
                .filter(t -> t.getCreatedAtTicket() != null && t.getCreatedAtTicket().isBefore(start))
                .toList();

        List<Ticket> filtered = population.stream()
                .filter(t -> statusFilter == null || t.getStatusTicket().getNameStatus().equalsIgnoreCase(statusFilter))
                .filter(t -> areaFilter == null || t.getAreaTicket().getNameArea().equalsIgnoreCase(areaFilter))
                .filter(t -> categoryFilter == null || t.getCategoryTicket().getNameCategory().equalsIgnoreCase(categoryFilter))
                .toList();

        // SLA de todos los tickets de una vez. Antes se pedía ticket a ticket, y
        // dos veces por ticket: con un año de tickets eran miles de consultas.
        Map<UUID, TicketSla> slas = slaPorTicket(ampliada);

        long total = population.size();
        long open = filtered.stream()
                .filter(t -> !"CERRADO".equals(estado(t)))
                .count();
        long resolved = population.stream()
                .filter(t -> "RESUELTO".equals(estado(t)))
                .count();
        long critical = filtered.stream()
                .filter(t -> "CRITICA".equals(prioridad(t)) && !"CERRADO".equals(estado(t)))
                .count();

        Double slaCompliance = cumplimientoSla(filtered, slas);
        Double avgResolutionHours = horasMediasResolucion(filtered);

        // Alertas SLA sobre tickets abiertos
        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime riskLimit = ahora.plusHours(2);
        long slaBreachedOpen = 0;
        long slaAtRisk = 0;
        for (Ticket t : filtered) {
            if (TERMINADOS.contains(estado(t))) {
                continue;
            }
            TicketSla ts = slas.get(t.getIdTicket());
            if (ts == null) {
                continue;
            }
            if (Boolean.TRUE.equals(ts.getResolutionBreached())) {
                slaBreachedOpen++;
            } else if (ts.getResolutionDueAt() != null
                    && !ts.getResolutionDueAt().isBefore(ahora)
                    && ts.getResolutionDueAt().isBefore(riskLimit)) {
                slaAtRisk++;
            }
        }

        Map<String, Long> byStatus = agrupar(population, this::estado);
        Map<String, Long> byArea = agrupar(population, t -> t.getAreaTicket().getNameArea());
        Map<String, Long> byCategory = agrupar(population, t -> t.getCategoryTicket().getNameCategory());
        Map<String, Long> byPriority = agrupar(population, this::prioridad);

        // ---- 12 semanas: creados y resueltos, con su fecha ----
        List<Integer> last12Weeks = new ArrayList<>();
        List<Integer> resolved12Weeks = new ArrayList<>();
        List<String> weekLabels = new ArrayList<>();
        OffsetDateTime weekStart = OffsetDateTime.now().with(java.time.DayOfWeek.MONDAY)
                .toLocalDate().minusWeeks(11).atStartOfDay().atOffset(ZoneOffset.UTC);
        for (int i = 0; i < 12; i++) {
            OffsetDateTime weekFrom = weekStart.plusWeeks(i);
            OffsetDateTime weekTo = weekFrom.plusWeeks(1);
            last12Weeks.add((int) population.stream()
                    .filter(t -> t.getCreatedAtTicket() != null
                            && !t.getCreatedAtTicket().isBefore(weekFrom) && t.getCreatedAtTicket().isBefore(weekTo))
                    .count());
            // Por fecha de cierre, sobre los dos periodos: un ticket creado antes
            // del rango que se cierra esta semana también cuenta como trabajo hecho.
            resolved12Weeks.add((int) ampliada.stream()
                    .filter(t -> t.getClosingDateTicket() != null
                            && !t.getClosingDateTicket().isBefore(weekFrom) && t.getClosingDateTicket().isBefore(weekTo))
                    .count());
            weekLabels.add(ETIQUETA_SEMANA.format(weekFrom.atZoneSameInstant(ZONE)).replace(".", ""));
        }

        // ---- Periodo anterior ----
        boolean hayAnterior = !anterior.isEmpty();
        Long prevTotal = hayAnterior ? (long) anterior.size() : null;
        Long prevResolved = hayAnterior
                ? anterior.stream().filter(t -> "RESUELTO".equals(estado(t))).count() : null;
        Double prevAvgResolution = hayAnterior ? horasMediasResolucion(anterior) : null;
        Double prevSla = hayAnterior ? cumplimientoSla(anterior, slas) : null;

        // ---- Antigüedad de lo pendiente ----
        Map<String, Long> aging = new LinkedHashMap<>();
        aging.put("< 1 día", 0L);
        aging.put("1-3 días", 0L);
        aging.put("3-7 días", 0L);
        aging.put("> 7 días", 0L);
        for (Ticket t : filtered) {
            if (TERMINADOS.contains(estado(t)) || t.getCreatedAtTicket() == null) {
                continue;
            }
            long horas = Duration.between(t.getCreatedAtTicket(), ahora).toHours();
            String tramo = horas < 24 ? "< 1 día" : horas < 72 ? "1-3 días" : horas < 168 ? "3-7 días" : "> 7 días";
            aging.merge(tramo, 1L, Long::sum);
        }

        // ---- Mapa de calor: día de la semana x hora, en hora de Ecuador ----
        int[][] calor = new int[7][24];
        for (Ticket t : population) {
            if (t.getCreatedAtTicket() == null) {
                continue;
            }
            var local = t.getCreatedAtTicket().atZoneSameInstant(ZONE);
            calor[local.getDayOfWeek().getValue() - 1][local.getHour()]++;
        }
        List<List<Integer>> heatmap = new ArrayList<>();
        for (int[] dia : calor) {
            heatmap.add(java.util.Arrays.stream(dia).boxed().toList());
        }

        // ---- Satisfacción ----
        List<TicketRating> calificaciones = calificaciones(population);
        double[] notas = calificaciones.stream()
                .map(TicketRating::getOverallSatisfaction)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Integer::doubleValue)
                .toArray();
        Double avgSatisfaction = notas.length == 0 ? null
                : Math.round(java.util.Arrays.stream(notas).average().orElse(0) * 100) / 100.0;

        // ---- Carga por agente: solo la administración compara agentes ----
        List<DashboardStats.AgentLoad> agentLoad = List.of();
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            agentLoad = cargaPorAgente(filtered);
        }

        List<DashboardStats.TicketAtencion> attention = requierenAtencion(filtered, slas, ahora);

        return new DashboardStats(total, open, resolved, critical, slaCompliance,
                avgResolutionHours, slaBreachedOpen, slaAtRisk,
                byStatus, byArea, byCategory, last12Weeks,
                byPriority, weekLabels, resolved12Weeks,
                prevTotal, prevResolved, prevAvgResolution, prevSla,
                aging, heatmap, avgSatisfaction, notas.length, agentLoad, attention);
    }

    // ============================ Auxiliares del tablero ============================

    private String estado(Ticket t) {
        return t.getStatusTicket() == null ? "" : t.getStatusTicket().getNameStatus();
    }

    private String prioridad(Ticket t) {
        return t.getPriorityTicket() == null ? "SIN PRIORIDAD" : t.getPriorityTicket().getNamePriority();
    }

    private Map<String, Long> agrupar(List<Ticket> tickets, java.util.function.Function<Ticket, String> clave) {
        return tickets.stream().collect(Collectors.groupingBy(clave, LinkedHashMap::new, Collectors.counting()));
    }

    /** Porcentaje de tickets terminados sin incumplir el SLA de resolución; null si no hay terminados. */
    private Double cumplimientoSla(List<Ticket> tickets, Map<UUID, TicketSla> slas) {
        List<Ticket> terminados = tickets.stream().filter(t -> TERMINADOS.contains(estado(t))).toList();
        if (terminados.isEmpty()) {
            return null;
        }
        long ok = terminados.stream()
                .filter(t -> {
                    TicketSla ts = slas.get(t.getIdTicket());
                    return ts == null || !Boolean.TRUE.equals(ts.getResolutionBreached());
                })
                .count();
        return Math.round((double) ok / terminados.size() * 10000) / 100.0;
    }

    /** Horas medias entre la creación y el cierre; null si ninguno está cerrado. */
    private Double horasMediasResolucion(List<Ticket> tickets) {
        List<Duration> duraciones = tickets.stream()
                .filter(t -> t.getClosingDateTicket() != null && t.getCreatedAtTicket() != null)
                .map(t -> Duration.between(t.getCreatedAtTicket(), t.getClosingDateTicket()))
                .toList();
        return duraciones.isEmpty() ? null
                : Math.round(duraciones.stream().mapToLong(Duration::toMinutes)
                        .average().orElse(0) / 60.0 * 100) / 100.0;
    }

    private Map<UUID, TicketSla> slaPorTicket(List<Ticket> tickets) {
        Map<UUID, TicketSla> mapa = new java.util.HashMap<>();
        List<UUID> ids = tickets.stream().map(Ticket::getIdTicket).filter(java.util.Objects::nonNull).toList();
        for (int i = 0; i < ids.size(); i += LOTE) {
            List<TicketSla> lote = ticketSlaRepository.findByTicketIdTicketIn(ids.subList(i, Math.min(ids.size(), i + LOTE)));
            if (lote == null) {
                continue;
            }
            for (TicketSla ts : lote) {
                if (ts.getTicket() != null) {
                    mapa.put(ts.getTicket().getIdTicket(), ts);
                }
            }
        }
        return mapa;
    }

    private List<TicketRating> calificaciones(List<Ticket> tickets) {
        if (ticketRatingRepository == null) {
            return List.of();
        }
        List<TicketRating> todas = new ArrayList<>();
        List<UUID> ids = tickets.stream().map(Ticket::getIdTicket).filter(java.util.Objects::nonNull).toList();
        for (int i = 0; i < ids.size(); i += LOTE) {
            List<TicketRating> lote = ticketRatingRepository.findByTicketIdTicketIn(ids.subList(i, Math.min(ids.size(), i + LOTE)));
            if (lote != null) {
                todas.addAll(lote);
            }
        }
        return todas;
    }

    /** Los agentes con más trabajo (abierto + resuelto) del periodo, hasta ocho. */
    private List<DashboardStats.AgentLoad> cargaPorAgente(List<Ticket> tickets) {
        Map<UUID, List<Ticket>> porAgente = tickets.stream()
                .filter(t -> t.getAgentTicket() != null)
                .collect(Collectors.groupingBy(t -> t.getAgentTicket().getIdUser()));
        List<DashboardStats.AgentLoad> carga = new ArrayList<>();
        for (List<Ticket> suyos : porAgente.values()) {
            com.mesaayuda.entity.User agente = suyos.get(0).getAgentTicket();
            long abiertos = suyos.stream().filter(t -> !TERMINADOS.contains(estado(t))).count();
            long resueltos = suyos.size() - abiertos;
            carga.add(new DashboardStats.AgentLoad(agente.getIdUser(), agente.getFullName(),
                    agente.getPhotoUrl(), abiertos, resueltos, horasMediasResolucion(suyos)));
        }
        carga.sort(java.util.Comparator
                .comparingLong((DashboardStats.AgentLoad c) -> c.openTickets() + c.resolvedTickets())
                .reversed());
        return carga.size() > 8 ? carga.subList(0, 8) : carga;
    }

    /** Abiertos con SLA: primero los vencidos, después los que vencen antes. Hasta seis. */
    private List<DashboardStats.TicketAtencion> requierenAtencion(List<Ticket> tickets, Map<UUID, TicketSla> slas,
                                                                  OffsetDateTime ahora) {
        return tickets.stream()
                .filter(t -> !TERMINADOS.contains(estado(t)))
                .filter(t -> slas.get(t.getIdTicket()) != null && slas.get(t.getIdTicket()).getResolutionDueAt() != null)
                .sorted(java.util.Comparator
                        .comparing((Ticket t) -> !Boolean.TRUE.equals(slas.get(t.getIdTicket()).getResolutionBreached()))
                        .thenComparing(t -> slas.get(t.getIdTicket()).getResolutionDueAt()))
                .limit(6)
                .map(t -> {
                    TicketSla ts = slas.get(t.getIdTicket());
                    boolean vencido = Boolean.TRUE.equals(ts.getResolutionBreached())
                            || ts.getResolutionDueAt().isBefore(ahora);
                    return new DashboardStats.TicketAtencion(t.getIdTicket(), t.getNumberTicket(), t.getTitleTicket(), prioridad(t),
                            estado(t), ts.getResolutionDueAt(), vencido,
                            t.getAgentTicket() == null ? null : t.getAgentTicket().getFullName());
                })
                .toList();
    }

    public DashboardOperations getOperations() {
        OffsetDateTime startOfDay = LocalDate.now(ZONE).atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime endOfDay = startOfDay.plusDays(1);

        List<Ticket> open = ticketRepository.findOpenTickets();
        long unassigned = open.stream()
                .filter(t -> t.getAgentTicket() == null)
                .count();
        long critical = open.stream()
                .filter(t -> "CRITICA".equals(t.getPriorityTicket().getNamePriority()))
                .count();

        OffsetDateTime riskLimit = OffsetDateTime.now().plusHours(2);
        long slaBreached = 0;
        long slaAtRisk = 0;
        Map<UUID, TicketSla> slas = slaPorTicket(open);
        for (Ticket t : open) {
            TicketSla ts = slas.get(t.getIdTicket());
            if (ts == null) {
                continue;
            }
            if (Boolean.TRUE.equals(ts.getResolutionBreached())) {
                slaBreached++;
            } else if (ts.getResolutionDueAt() != null
                    && !ts.getResolutionDueAt().isBefore(OffsetDateTime.now())
                    && ts.getResolutionDueAt().isBefore(riskLimit)) {
                slaAtRisk++;
            }
        }

        long createdToday = ticketRepository.countCreatedBetween(startOfDay, endOfDay);
        long resolvedToday = ticketRepository.countResolvedBetween(startOfDay, endOfDay);

        return new DashboardOperations(open.size(), unassigned, critical, slaAtRisk,
                slaBreached, createdToday, resolvedToday, sseEventBroker.recentEvents());
    }
}