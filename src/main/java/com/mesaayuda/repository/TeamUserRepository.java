package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.TeamUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamUserRepository extends JpaRepository<TeamUser, UUID> {

    List<TeamUser> findByTeamIdTeamAndActiveTeamUserTrue(UUID teamId);

    /** Todas las filas del equipo, también las desactivadas (para no volver a añadir a quien se quitó). */
    List<TeamUser> findByTeamIdTeam(UUID teamId);

    Optional<TeamUser> findByTeamIdTeamAndUserIdUser(UUID teamId, UUID userId);

    boolean existsByTeamIdTeamAndUserIdUser(UUID teamId, UUID userId);

    /**
     * Agente con menos tickets asignados. Los que nunca han recibido uno
     * (lastAssignedAt NULL) van primero: en PostgreSQL NULLS_sortan al final
     * en ASC, por eso se ordena explícitamente con un CASE.
     */
    @Query("SELECT t FROM TeamUser t WHERE t.team.idTeam = :teamId AND t.activeTeamUser = true " +
           "AND t.user.statusUser = 'ACTIVE' AND t.user.deletedUser = false " +
           "AND t.user.rolUser.nameRole = 'AGENT' " +
           "ORDER BY CASE WHEN t.lastAssignedAt IS NULL THEN 0 ELSE 1 END ASC, " +
           "t.lastAssignedAt ASC, t.idTeamUser ASC")
    List<TeamUser> findLeastAssigned(@Param("teamId") UUID teamId, Pageable pageable);

    @Modifying
    @Query("UPDATE TeamUser t SET t.lastAssignedAt = :when WHERE t.idTeamUser = :id")
    void updateLastAssignedAt(@Param("id") UUID id, @Param("when") OffsetDateTime when);
}