package com.mesaayuda.repository;

import com.mesaayuda.entity.SeasonalTheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonalThemeRepository extends JpaRepository<SeasonalTheme, UUID> {

    Optional<SeasonalTheme> findByCodeTheme(String codeTheme);

    List<SeasonalTheme> findAllByOrderByStartMonthThemeAscStartDayThemeAsc();
}
