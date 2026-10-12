package com.mesaayuda.dto;

import java.util.List;

/**
 * Respuesta de la búsqueda global ({@code GET /search}).
 *
 * <p>Cada grupo se resuelve con los permisos del rol que consulta y se limita a
 * cinco elementos. Los grupos que el rol no puede ver llegan como lista vacía y
 * nunca como null, para que el frontend no tenga que distinguir ambos casos.
 *
 * @param query     el término tal y como se consultó
 * @param tickets   tickets que coinciden (un solicitante solo ve los suyos)
 * @param users     solicitantes (rol USER); solo se sirve al personal
 * @param agents    agentes (rol AGENT); solo se sirve al personal
 * @param teams     equipos de soporte activos
 * @param areas     áreas no borradas y activas
 * @param workflows procesos automatizados; solo se sirve a SUPER_ADMIN
 */
public record GlobalSearchResponse(
        String query,
        List<SearchTicketItem> tickets,
        List<SearchUserItem> users,
        List<SearchUserItem> agents,
        List<SearchTeamItem> teams,
        List<SearchAreaItem> areas,
        List<SearchWorkflowItem> workflows) {

    public GlobalSearchResponse {
        tickets = tickets == null ? List.of() : tickets;
        users = users == null ? List.of() : users;
        agents = agents == null ? List.of() : agents;
        teams = teams == null ? List.of() : teams;
        areas = areas == null ? List.of() : areas;
        workflows = workflows == null ? List.of() : workflows;
    }
}