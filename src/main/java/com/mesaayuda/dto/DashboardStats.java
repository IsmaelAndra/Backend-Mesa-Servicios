package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicadores del tablero, ya acotados al alcance del rol (el agente ve lo suyo,
 * el solicitante sus tickets, la administración todo).
 *
 * <p>Los campos {@code prev*} son el mismo indicador en el periodo anterior de
 * igual duración, para mostrar la tendencia; son null cuando ese periodo no
 * tiene datos con los que comparar.
 */
public record DashboardStats(
        long totalTickets,
        long openTickets,
        long resolvedTickets,
        long criticalTickets,
        Double slaComplianceRate,
        Double avgResolutionHours,
        long slaBreachedOpen,
        long slaAtRisk,
        Map<String, Long> ticketsByStatus,
        Map<String, Long> ticketsByArea,
        Map<String, Long> ticketsByCategory,
        List<Integer> ticketsLast12Weeks,
        // ---- Ampliación del tablero ----
        Map<String, Long> ticketsByPriority,
        /** Etiqueta de cada una de las 12 semanas ("6 oct"), en el mismo orden. */
        List<String> weekLabels,
        /** Tickets cerrados o resueltos en cada una de las 12 semanas. */
        List<Integer> resolvedLast12Weeks,
        Long prevTotalTickets,
        Long prevResolvedTickets,
        Double prevAvgResolutionHours,
        Double prevSlaComplianceRate,
        /** Abiertos según su antigüedad: "< 1 día", "1-3 días", "3-7 días", "> 7 días". */
        Map<String, Long> backlogAging,
        /** Tickets creados por día de la semana (lunes primero) y hora del día (0-23). */
        List<List<Integer>> activityHeatmap,
        /** Satisfacción media (1 a 5) de las calificaciones del periodo; null sin calificaciones. */
        Double avgSatisfaction,
        long ratingsCount,
        /** Carga por agente (solo administración; vacío para el resto). */
        List<AgentLoad> agentLoad,
        /** Tickets abiertos que necesitan atención: SLA vencido primero, luego los que vencen antes. */
        List<TicketAtencion> attention
) {

    public record AgentLoad(UUID agentId, String name, String photoUrl,
                            long openTickets, long resolvedTickets, Double avgResolutionHours) {
    }

    public record TicketAtencion(UUID ticketId, Long ticketNumber, String title, String priority, String status,
                                 OffsetDateTime dueAt, boolean breached, String agentName) {
    }
}
