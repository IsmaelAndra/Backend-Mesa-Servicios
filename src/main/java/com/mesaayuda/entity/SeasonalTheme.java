package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tema de temporada (Halloween, Navidad...). Se activa cada año entre su día y
 * mes de inicio y de fin; si el fin es anterior al inicio, cruza el año.
 * El código (HALLOWEEN, NAVIDAD) es el que el frontend usa para los adornos.
 */
@Entity
@Table(name = "seasonal_theme")
@Getter
@Setter
@NoArgsConstructor
public class SeasonalTheme {

    @Id
    @Column(name = "id_theme", updatable = false)
    private UUID idTheme;

    @Column(name = "code_theme", nullable = false, length = 30, updatable = false)
    private String codeTheme;

    @Column(name = "name_theme", nullable = false, length = 80)
    private String nameTheme;

    @Column(name = "active_theme", nullable = false)
    private Boolean activeTheme = true;

    @Column(name = "start_month_theme", nullable = false)
    private Integer startMonthTheme;

    @Column(name = "start_day_theme", nullable = false)
    private Integer startDayTheme;

    @Column(name = "end_month_theme", nullable = false)
    private Integer endMonthTheme;

    @Column(name = "end_day_theme", nullable = false)
    private Integer endDayTheme;

    /**
     * Regla para fechas móviles. CARNAVAL: del sábado al martes de carnaval,
     * calculado cada año a partir de la Pascua; los días y meses no se usan.
     */
    @Column(name = "rule_theme", length = 20, updatable = false)
    private String ruleTheme;

    @Column(name = "greeting_theme", length = 200)
    private String greetingTheme;

    @Column(name = "background_url_theme", length = 500)
    private String backgroundUrlTheme;

    @Column(name = "logo_url_theme", length = 500)
    private String logoUrlTheme;

    @Column(name = "created_at_theme", nullable = false, updatable = false)
    private OffsetDateTime createdAtTheme;

    @Column(name = "updated_at_theme", nullable = false)
    private OffsetDateTime updatedAtTheme;
}
