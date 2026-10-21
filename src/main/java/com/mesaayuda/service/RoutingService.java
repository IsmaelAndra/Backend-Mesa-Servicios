package com.mesaayuda.service;

import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.TeamUser;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TeamUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private static final String METHOD_ROUND_ROBIN = "ROUND_ROBIN";
    private static final String METHOD_LEAST_ASSIGNED = "LEAST_ASSIGNED";
    private static final String METHOD_MANUAL = "MANUAL";

    @Autowired
    private TeamUserRepository teamUserRepository;

    @Autowired
    private RoutingConfigService routingConfigService;

    @Autowired
    private com.mesaayuda.repository.UserRepository userRepository;

    /**
     * Equipo "con todos los agentes": añade como miembro a cada agente activo que
     * aún no tenga fila en el equipo. Quien fue quitado (fila inactiva) no se
     * vuelve a añadir: quitar a alguien a mano se respeta.
     */
    public void sincronizarAgentes(SupportTeam team) {
        if (team == null || team.getIdTeam() == null || !Boolean.TRUE.equals(team.getAutoMembersTeam())
                || userRepository == null) {
            return;
        }
        java.util.Set<UUID> yaEstan = new java.util.HashSet<>();
        for (TeamUser tu : teamUserRepository.findByTeamIdTeam(team.getIdTeam())) {
            if (tu.getUser() != null) {
                yaEstan.add(tu.getUser().getIdUser());
            }
        }
        for (User agente : userRepository.findByRoleName("AGENT")) {
            if (yaEstan.contains(agente.getIdUser())
                    || Boolean.TRUE.equals(agente.getDeletedUser())
                    || !"ACTIVE".equals(agente.getStatusUser())) {
                continue;
            }
            TeamUser nuevo = new TeamUser();
            nuevo.setTeam(team);
            nuevo.setUser(agente);
            nuevo.setActiveTeamUser(true);
            teamUserRepository.save(nuevo);
            log.info("Agente {} añadido al equipo '{}' (incluye a todos los agentes)",
                    agente.getCorporateEmailUser(), team.getNameTeam());
        }
    }

    /**
     * Enrutamiento automático: determina el equipo destino según la categoría,
     * luego asigna un agente según el método de asignación del equipo.
     * <p>
     * Si el SUPER_ADMIN desactiva la asignación automática en la configuración
     * global de enrutamiento, el método devuelve {@code null} y el ticket queda
     * en la cola sin asignar.
     *
     * @return el agente asignado (puede ser null si no hay agente disponible)
     */
    public User routeTicket(Category category) {
        if (category == null) {
            log.warn("Enrutamiento sin categoría");
            return null;
        }

        if (!routingConfigService.isAutoAssignEnabled()) {
            log.info("Asignación automática desactivada por el SUPER_ADMIN; ticket sin enrutar");
            return null;
        }

        SupportTeam team = category.getTeamCategory();
        if (team == null || !Boolean.TRUE.equals(team.getActiveTeam())) {
            log.info("Categoría '{}' sin equipo activo de soporte", category.getNameCategory());
            return null;
        }
        return routeTeam(team);
    }

    /**
     * Elige agente dentro de un equipo concreto, con el método del equipo
     * (rotación, menos asignado o manual).
     *
     * <p>No mira el interruptor global de asignación automática: lo usa la acción
     * "asignar equipo" de un workflow, que es una decisión explícita de quien lo
     * diseñó, no el enrutamiento por defecto al crear un ticket. Un equipo en
     * modo MANUAL sigue devolviendo null: el ticket queda en su cola sin agente.
     */
    public User routeTeam(SupportTeam team) {
        if (team == null || !Boolean.TRUE.equals(team.getActiveTeam())) {
            return null;
        }
        // Los agentes creados después de configurar el equipo entran aquí.
        sincronizarAgentes(team);

        // Orden estable por id: la rotación no puede depender del orden en que
        // la base devuelva las filas.
        List<TeamUser> members = teamUserRepository
                .findByTeamIdTeamAndActiveTeamUserTrue(team.getIdTeam())
                .stream()
                .filter(RoutingService::isAssignable)
                .sorted(Comparator.comparing(TeamUser::getIdTeamUser,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (members.isEmpty()) {
            log.info("Equipo '{}' sin agentes disponibles para asignar", team.getNameTeam());
            return null;
        }

        String method = team.getAssignmentMethod() == null
                ? METHOD_ROUND_ROBIN
                : team.getAssignmentMethod().trim().toUpperCase();

        User selected;
        TeamUser target;
        switch (method) {
            case METHOD_MANUAL -> {
                log.info("Equipo '{}' usa asignación MANUAL", team.getNameTeam());
                return null;
            }
            case METHOD_LEAST_ASSIGNED -> {
                List<TeamUser> least = teamUserRepository
                        .findLeastAssigned(team.getIdTeam(), PageRequest.of(0, 1));
                target = least.isEmpty() ? null : least.get(0);
            }
            default -> target = pickRoundRobin(members);
        }

        // Si la consulta no devolvió nada utilizable, se toma el primero de la lista estable.
        target = (target != null && target.getUser() != null) ? target : members.get(0);
        selected = target.getUser();

        teamUserRepository.updateLastAssignedAt(target.getIdTeamUser(), OffsetDateTime.now());
        return selected;
    }

    /**
     * Rotación: toma el siguiente, en la lista estable, al último agente al que se
     * le asignó. Antes tomaba "el primero que no fuera el último", y con tres o
     * más agentes la rotación quedaba entre los dos primeros: el tercero no
     * recibía nunca un ticket.
     */
    static TeamUser pickRoundRobin(List<TeamUser> members) {
        if (members.size() == 1) {
            return members.get(0);
        }
        UUID lastAssignedId = getLastAssignedId(members);
        if (lastAssignedId == null) {
            return members.get(0);
        }
        for (int i = 0; i < members.size(); i++) {
            if (Objects.equals(members.get(i).getIdTeamUser(), lastAssignedId)) {
                return members.get((i + 1) % members.size());
            }
        }
        return members.get(0);
    }

    private static UUID getLastAssignedId(List<TeamUser> members) {
        return members.stream()
                .filter(tu -> tu.getLastAssignedAt() != null)
                .max(Comparator.comparing(TeamUser::getLastAssignedAt))
                .map(TeamUser::getIdTeamUser)
                .orElse(null);
    }

    /** Solo entran usuarios activos, no eliminados y con rol de agente. */
    private static boolean isAssignable(TeamUser tu) {
        User user = tu.getUser();
        return user != null
                && "ACTIVE".equals(user.getStatusUser())
                && !Boolean.TRUE.equals(user.getDeletedUser())
                && user.getRolUser() != null
                && "AGENT".equals(user.getRolUser().getNameRole());
    }
}
