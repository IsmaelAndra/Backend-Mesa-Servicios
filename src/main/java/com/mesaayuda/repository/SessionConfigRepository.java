package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.SessionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionConfigRepository extends JpaRepository<SessionConfig, UUID> {

    /** Configuracion vigente. En teoria hay una sola fila, la de id 1. */
    Optional<SessionConfig> findTopByOrderByIdSessionConfigAsc();
}
