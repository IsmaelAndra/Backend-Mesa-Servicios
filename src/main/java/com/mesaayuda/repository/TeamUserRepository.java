package com.mesaayuda.repository;

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
public interface TeamUserRepository extends JpaRepository<TeamUser, Long> {

    List<TeamUser> findByTeamIdTeamAndActiveTeamUserTrue(Long teamId);

    Optional<TeamUser> findByTeamIdTeamAndUserIdUser(Long teamId, Long userId);

    boolean existsByTeamIdTeamAndUserIdUser(Long teamId, Long userId);

    @Query("SELECT t FROM TeamUser t WHERE t.team.idTeam = :teamId AND t.activeTeamUser = true " +
           "AND t.user.statusUser = 'ACTIVE' AND t.user.deletedUser = false ORDER BY t.lastAssignedAt ASC")
    List<TeamUser> findLeastAssigned(@Param("teamId") Long teamId, Pageable pageable);

    @Modifying
    @Query("UPDATE TeamUser t SET t.lastAssignedAt = :when WHERE t.idTeamUser = :id")
    void updateLastAssignedAt(@Param("id") Long id, @Param("when") OffsetDateTime when);
}