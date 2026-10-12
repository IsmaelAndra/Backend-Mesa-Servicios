package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.RoutingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoutingConfigRepository extends JpaRepository<RoutingConfig, UUID> {

    Optional<RoutingConfig> findById(UUID id);
}
