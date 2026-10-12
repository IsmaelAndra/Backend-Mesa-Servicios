package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.ErrorPageConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Textos de las pantallas de error. Singleton: solo hay una fila, la de id 1.
 */
public interface ErrorPageConfigRepository extends JpaRepository<ErrorPageConfig, UUID> {

    Optional<ErrorPageConfig> findTopByOrderByIdErrorPageAsc();
}