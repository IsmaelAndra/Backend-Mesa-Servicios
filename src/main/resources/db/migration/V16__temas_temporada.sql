-- ============================================================================
-- Temas de temporada (Halloween, Navidad...).
--
-- Cada tema se activa solo entre sus fechas (día y mes, se repite cada año; si
-- el fin es anterior al inicio cruza el año, p. ej. 27/12 → 02/01). El tema
-- añade adornos sin cambiar los colores de marca. Fondo y logo propios son
-- opcionales: sin ellos se usan los de Marca.
--
-- seasonal_theme_config (una fila): interruptor general y tema forzado para
-- probarlo fuera de sus fechas. Lo gestiona el SUPER_ADMIN.
-- ============================================================================

CREATE TABLE IF NOT EXISTS seasonal_theme (
    id_theme          UUID PRIMARY KEY,
    code_theme        VARCHAR(30)  NOT NULL,
    name_theme        VARCHAR(80)  NOT NULL,
    active_theme      BOOLEAN      NOT NULL DEFAULT TRUE,
    start_month_theme INTEGER      NOT NULL,
    start_day_theme   INTEGER      NOT NULL,
    end_month_theme   INTEGER      NOT NULL,
    end_day_theme     INTEGER      NOT NULL,
    greeting_theme    VARCHAR(200),
    background_url_theme VARCHAR(500),
    logo_url_theme    VARCHAR(500),
    created_at_theme  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at_theme  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_seasonal_theme_code UNIQUE (code_theme),
    CONSTRAINT ck_seasonal_theme_fechas CHECK (
        start_month_theme BETWEEN 1 AND 12 AND end_month_theme BETWEEN 1 AND 12
        AND start_day_theme BETWEEN 1 AND 31 AND end_day_theme BETWEEN 1 AND 31)
);

CREATE TABLE IF NOT EXISTS seasonal_theme_config (
    id_theme_config     UUID PRIMARY KEY,
    enabled_theme_config BOOLEAN     NOT NULL DEFAULT TRUE,
    forced_code_theme   VARCHAR(30),
    updated_by_theme    UUID,
    updated_at_theme_config TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO seasonal_theme_config (id_theme_config, enabled_theme_config)
VALUES ('00000000-0000-0000-0000-000000000008', TRUE)
ON CONFLICT (id_theme_config) DO NOTHING;

INSERT INTO seasonal_theme (id_theme, code_theme, name_theme, start_month_theme, start_day_theme,
                            end_month_theme, end_day_theme, greeting_theme)
VALUES
  ('a0000000-0000-0000-0000-000000000001', 'HALLOWEEN', 'Halloween', 10, 20, 10, 31,
   '¡Feliz Halloween! Que hoy solo te asusten los tickets resueltos.'),
  ('a0000000-0000-0000-0000-000000000002', 'NAVIDAD', 'Navidad', 12, 1, 12, 26,
   '¡Felices fiestas! Gracias por todo el apoyo de este año.')
ON CONFLICT (code_theme) DO NOTHING;
