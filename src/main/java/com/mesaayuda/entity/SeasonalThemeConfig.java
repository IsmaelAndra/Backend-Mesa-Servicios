package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Interruptor general de los temas de temporada y tema forzado (una sola fila). */
@Entity
@Table(name = "seasonal_theme_config")
@Getter
@Setter
@NoArgsConstructor
public class SeasonalThemeConfig {

    @Id
    @Column(name = "id_theme_config", updatable = false)
    private UUID idThemeConfig = IdsConfiguracion.TEMAS_TEMPORADA;

    @Column(name = "enabled_theme_config", nullable = false)
    private Boolean enabledThemeConfig = true;

    /** Código del tema que se muestra siempre, aunque no sea su fecha (para probarlo). */
    @Column(name = "forced_code_theme", length = 30)
    private String forcedCodeTheme;

    @Column(name = "updated_by_theme")
    private UUID updatedByTheme;

    @Column(name = "updated_at_theme_config", nullable = false)
    private OffsetDateTime updatedAtThemeConfig;
}
