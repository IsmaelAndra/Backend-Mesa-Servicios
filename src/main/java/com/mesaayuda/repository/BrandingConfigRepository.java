package com.mesaayuda.repository;

import com.mesaayuda.entity.BrandingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandingConfigRepository extends JpaRepository<BrandingConfig, Long> {

    Optional<BrandingConfig> findTopByOrderByIdBrandingAsc();
}