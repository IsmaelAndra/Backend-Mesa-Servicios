package com.mesaayuda.repository;

import com.mesaayuda.entity.AiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {
    Optional<AiConfig> findFirstByOrderByIdAiConfigAsc();
}