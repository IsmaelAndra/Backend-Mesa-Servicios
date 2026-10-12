package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.SupportTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTeamRepository extends JpaRepository<SupportTeam, UUID>,
        JpaSpecificationExecutor<SupportTeam> {
    Optional<SupportTeam> findByNameTeam(String nameTeam);
    boolean existsByNameTeam(String nameTeam);
    List<SupportTeam> findByActiveTeamTrue();
}