package com.mesaayuda.service;

import java.util.Set;
import java.util.UUID;

/**
 * Qué tickets puede ver una persona (mesa multi-área, V28; confidenciales, V29).
 *
 * <ul>
 *   <li>{@code veTodo}: súper admin y auditor, salvo los confidenciales.</li>
 *   <li>Siempre los suyos: los que pidió y los que tiene asignados.</li>
 *   <li>{@code areasQueAtiende}: los tickets que atienden esas áreas (el agente,
 *       la suya; el jefe, las que tiene a cargo).</li>
 *   <li>{@code areasQueSigue}: los tickets que PIDE la gente de esas áreas, aunque
 *       los atienda otra (seguimiento del jefe, solo lectura).</li>
 *   <li>{@code areasACargo}: áreas de las que es jefe; ve sus confidenciales.</li>
 *   <li>{@code confidencialesPermitidos}: confidenciales a los que se le autorizó
 *       (persona añadida o acceso de emergencia aprobado y vigente).</li>
 * </ul>
 *
 * <p>Un confidencial solo lo ven el solicitante, el agente asignado, el jefe del
 * área que lo atiende, las personas autorizadas y, si se abrió al área, los
 * agentes de esa área. Ni el seguimiento del jefe ni el "ve todo" del súper
 * admin bastan.
 */
public record AlcanceTickets(boolean veTodo, UUID userId, Set<UUID> areasQueAtiende, Set<UUID> areasQueSigue,
                             Set<UUID> areasACargo, Set<UUID> confidencialesPermitidos) {

    public AlcanceTickets {
        areasQueAtiende = areasQueAtiende == null ? Set.of() : Set.copyOf(areasQueAtiende);
        areasQueSigue = areasQueSigue == null ? Set.of() : Set.copyOf(areasQueSigue);
        areasACargo = areasACargo == null ? Set.of() : Set.copyOf(areasACargo);
        confidencialesPermitidos = confidencialesPermitidos == null ? Set.of() : Set.copyOf(confidencialesPermitidos);
    }

    /** Sin jefaturas ni confidenciales autorizados. */
    public AlcanceTickets(boolean veTodo, UUID userId, Set<UUID> areasQueAtiende, Set<UUID> areasQueSigue) {
        this(veTodo, userId, areasQueAtiende, areasQueSigue, Set.of(), Set.of());
    }

    public static AlcanceTickets todo(UUID userId) {
        return new AlcanceTickets(true, userId, Set.of(), Set.of());
    }

    public static AlcanceTickets soloPropios(UUID userId) {
        return new AlcanceTickets(false, userId, Set.of(), Set.of());
    }

    /** Si un ticket NO confidencial con estos datos entra en el alcance. */
    public boolean incluye(UUID solicitanteId, UUID agenteId, UUID areaQueAtiendeId, UUID areaSolicitanteId) {
        if (veTodo) {
            return true;
        }
        if (userId != null && (userId.equals(solicitanteId) || userId.equals(agenteId))) {
            return true;
        }
        return (areaQueAtiendeId != null && areasQueAtiende.contains(areaQueAtiendeId))
                || (areaSolicitanteId != null && areasQueSigue.contains(areaSolicitanteId));
    }

    /** Si puede ver un ticket CONFIDENCIAL con estos datos. */
    public boolean veConfidencial(UUID ticketId, UUID solicitanteId, UUID agenteId, UUID areaQueAtiendeId,
                                  boolean abiertoAlArea) {
        if (userId != null && (userId.equals(solicitanteId) || userId.equals(agenteId))) {
            return true;
        }
        if (areaQueAtiendeId != null && areasACargo.contains(areaQueAtiendeId)) {
            return true;
        }
        if (abiertoAlArea && areaQueAtiendeId != null && areasQueAtiende.contains(areaQueAtiendeId)) {
            return true;
        }
        return ticketId != null && confidencialesPermitidos.contains(ticketId);
    }
}
