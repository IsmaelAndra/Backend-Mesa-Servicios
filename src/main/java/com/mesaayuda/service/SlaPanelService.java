package com.mesaayuda.service;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketSlaRepository;
import com.mesaayuda.security.Roles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Panel de SLA en tiempo real (V49): los tickets abiertos con su plazo de
 * resolución, cuánto llevan consumido (en horas hábiles si la política usa
 * horario laboral) y la carga por agente. El jefe ve lo que atiende su área;
 * el SUPER_ADMIN y el AUDITOR, todo. Los confidenciales salen sin título para
 * quien no los puede leer.
 */
@Service
public class SlaPanelService {

    /** Desde este porcentaje consumido el ticket está "en riesgo". */
    public static final int UMBRAL_RIESGO = 80;

    private final TicketSlaRepository slas;
    private final UserService userService;
    private final JefaturaService jefatura;
    private final TicketAccessPolicy acceso;
    private final SlaService slaService;

    public SlaPanelService(TicketSlaRepository slas, UserService userService, JefaturaService jefatura,
                           TicketAccessPolicy acceso, SlaService slaService) {
        this.slas = slas;
        this.userService = userService;
        this.jefatura = jefatura;
        this.acceso = acceso;
        this.slaService = slaService;
    }

    public record Item(UUID id, Long numero, String titulo, boolean confidencial, String prioridad,
                       String prioridadColor, String estado, String agente, String area, String politica,
                       boolean horarioLaboral, OffsetDateTime respuestaVence, boolean respondido,
                       OffsetDateTime resolucionVence, int pctConsumido, long minutosRestantes, String estadoSla,
                       OffsetDateTime creado) {
    }

    public record Agente(String nombre, long abiertos, long vencidos, long enRiesgo) {
    }

    public record Panel(OffsetDateTime generado, long total, long vencidos, long enRiesgo, long enTiempo,
                        long sinAgente, long sinRespuesta, List<Agente> porAgente, List<Item> tickets,
                        int umbralRiesgo) {
    }

    @Transactional(readOnly = true)
    public Panel panel() {
        User yo = userService.getCurrentUser();
        String rol = yo.getRolUser() == null ? "" : yo.getRolUser().getNameRole();
        boolean todo = Roles.esSuperAdmin(rol) || Roles.AUDITOR.equals(rol);
        Set<UUID> areas = todo ? Set.of() : jefatura.areasACargo(yo);
        if (!todo && (!Roles.esJefe(rol) || areas.isEmpty())) {
            throw new AccessDeniedException("El panel de SLA es para jefes de área y súper administradores");
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        List<Item> items = new ArrayList<>();
        for (TicketSla s : slas.abiertosParaPanel()) {
            Ticket t = s.getTicket();
            if (!todo && (t.getAreaDestinoTicket() == null || !areas.contains(t.getAreaDestinoTicket().getIdArea()))) {
                continue;
            }
            if (s.getResolutionDueAt() == null) {
                continue;
            }
            boolean conf = TicketAccessPolicy.esConfidencial(t);
            boolean legible = !conf || acceso.puedeLeer(t, yo);
            long total = slaService.minutosQueCuentan(s.getSlaPolicy(), s.getCreatedAt(), s.getResolutionDueAt());
            long usado = slaService.minutosQueCuentan(s.getSlaPolicy(), s.getCreatedAt(), ahora);
            int pct = total <= 0 ? 100 : (int) Math.min(999, Math.round(usado * 100.0 / total));
            boolean vencido = !ahora.isBefore(s.getResolutionDueAt());
            long restantes = vencido
                    ? -slaService.minutosQueCuentan(s.getSlaPolicy(), s.getResolutionDueAt(), ahora)
                    : slaService.minutosQueCuentan(s.getSlaPolicy(), ahora, s.getResolutionDueAt());
            String estadoSla = vencido ? "VENCIDO" : pct >= UMBRAL_RIESGO ? "EN_RIESGO" : "EN_TIEMPO";
            items.add(new Item(t.getIdTicket(), t.getNumberTicket(),
                    legible ? t.getTitleTicket() : "Ticket confidencial", conf,
                    t.getPriorityTicket() == null ? null : t.getPriorityTicket().getNamePriority(),
                    t.getPriorityTicket() == null ? null : t.getPriorityTicket().getColorPriority(),
                    t.getStatusTicket() == null ? null : t.getStatusTicket().getNameStatus(),
                    t.getAgentTicket() == null ? null : nombre(t.getAgentTicket()),
                    t.getAreaDestinoTicket() == null ? null : AdminService.nombreVisible(t.getAreaDestinoTicket().getNameArea()),
                    s.getSlaPolicy().getNameSlaPolicy(), slaService.usaHorarioLaboral(s.getSlaPolicy()),
                    s.getResponseDueAt(), s.getResponseCompletedAt() != null,
                    s.getResolutionDueAt(), pct, restantes, estadoSla, t.getCreatedAtTicket()));
        }
        // Lo más urgente arriba: vencidos (los más atrasados primero), luego el menos tiempo restante.
        items.sort(Comparator.comparingLong(Item::minutosRestantes));

        Map<String, long[]> carga = new LinkedHashMap<>();
        for (Item i : items) {
            long[] c = carga.computeIfAbsent(i.agente() == null ? "Sin asignar" : i.agente(), k -> new long[3]);
            c[0]++;
            if ("VENCIDO".equals(i.estadoSla())) c[1]++;
            if ("EN_RIESGO".equals(i.estadoSla())) c[2]++;
        }
        List<Agente> porAgente = carga.entrySet().stream()
                .map(e -> new Agente(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingLong(Agente::vencidos).reversed()
                        .thenComparing(Comparator.comparingLong(Agente::enRiesgo).reversed())
                        .thenComparing(Comparator.comparingLong(Agente::abiertos).reversed()))
                .toList();
        long vencidos = items.stream().filter(i -> "VENCIDO".equals(i.estadoSla())).count();
        long riesgo = items.stream().filter(i -> "EN_RIESGO".equals(i.estadoSla())).count();
        return new Panel(ahora, items.size(), vencidos, riesgo, items.size() - vencidos - riesgo,
                items.stream().filter(i -> i.agente() == null).count(),
                items.stream().filter(i -> !i.respondido()).count(),
                porAgente, items, UMBRAL_RIESGO);
    }

    private static String nombre(User u) {
        return u.getDisplayName() != null && !u.getDisplayName().isBlank() ? u.getDisplayName() : u.getCorporateEmailUser();
    }
}
