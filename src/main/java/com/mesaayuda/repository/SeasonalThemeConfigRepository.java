package com.mesaayuda.repository;

import com.mesaayuda.entity.SeasonalThemeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeasonalThemeConfigRepository extends JpaRepository<SeasonalThemeConfig, UUID> {
}
