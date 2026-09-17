package com.mesaayuda.service;

import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.TeamUser;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.TeamUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    @Autowired
    private TeamUserRepository teamUserRepository;

    /**
     * Enrutamiento automático: determina el equipo destino según la categoría,
     * luego asigna un agente según el método de asignación del equipo.
     *
     * @return el agente asignado (puede ser null si no hay agente disponible)
     */
    public User routeTicket(Category category) {
        if (category == null) {
            log.warn("Enrutamiento sin categoría");
            return null;
        }
        SupportTeam team = category.getTeamCategory();
        if (team == null || !Boolean.TRUE.equals(team.getActiveTeam())) {
            log.info("Categoría '{}' sin equipo activo de soporte", category.getNameCategory());
            return null;
        }

        List<TeamUser> members = teamUserRepository
                .findByTeamIdTeamAndActiveTeamUserTrue(team.getIdTeam())
                .stream()
                .filter(tu -> tu.getUser() != null
                        && "ACTIVE".equals(tu.getUser().getStatusUser())
                        && !Boolean.TRUE.equals(tu.getUser().getDeletedUser()))
                .toList();

        if (members.isEmpty()) {
            return null;
        }

        User selected;
        switch (team.getAssignmentMethod() == null ? "ROUND_ROBIN" : team.getAssignmentMethod()) {
            case "LEAST_ASSIGNED" -> {
                List<TeamUser> least = teamUserRepository
                        .findLeastAssigned(team.getIdTeam(), PageRequest.of(0, 1));
                selected = least.isEmpty() ? null : least.get(0).getUser();
            }
            case "MANUAL" -> {
                log.info("Equipo '{}' usa asignación MANUAL", team.getNameTeam());
                return null;
            }
            default -> {
                TeamUser target = members.stream()
                        .filter(tu -> !Objects.equals(tu.getIdTeamUser(), getLastAssignedId(members)))
                        .findFirst()
                        .orElse(members.get(0));
                selected = target.getUser();
            }
        }

        if (selected == null && !members.isEmpty()) {
            selected = members.get(0).getUser();
        }

        if (selected != null) {
            TeamUser tu = teamUserRepository
                    .findByTeamIdTeamAndUserIdUser(team.getIdTeam(), selected.getIdUser())
                    .orElse(null);
            if (tu != null) {
                teamUserRepository.updateLastAssignedAt(tu.getIdTeamUser(), OffsetDateTime.now());
            }
        }

        return selected;
    }

    private Long getLastAssignedId(List<TeamUser> members) {
        return members.stream()
                .filter(tu -> tu.getLastAssignedAt() != null)
                .max((a, b) -> a.getLastAssignedAt().compareTo(b.getLastAssignedAt()))
                .map(TeamUser::getIdTeamUser)
                .orElse(null);
    }
}