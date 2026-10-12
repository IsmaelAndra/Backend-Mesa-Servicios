package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.PrivacyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Politica de privacidad editable. Singleton: solo hay una fila, la de id 1.
 */
public interface PrivacyConfigRepository extends JpaRepository<PrivacyConfig, UUID> {

    Optional<PrivacyConfig> findTopByOrderByIdPrivacyConfigAsc();
}