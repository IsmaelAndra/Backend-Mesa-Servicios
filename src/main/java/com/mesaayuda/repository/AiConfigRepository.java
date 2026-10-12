package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.AiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConfigRepository extends JpaRepository<AiConfig, UUID> {
    Optional<AiConfig> findFirstByOrderByIdAiConfigAsc();
}