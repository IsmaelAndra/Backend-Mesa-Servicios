-- ============================================================================
-- Configuración de la telefonía desde Administración (una sola fila).
--
-- La URL, el usuario y la clave de la API de la UCM se editan en la mesa
-- (Administración → Telefonía). La clave se guarda cifrada, como el secreto de
-- Microsoft Graph. El puente de telefonía solo conoce la URL de la mesa y su
-- token, y descarga desde aquí el resto de la configuración.
--
-- Del token del puente solo se guarda el SHA-256: se muestra una vez al
-- generarlo y no se puede recuperar.
-- ============================================================================

CREATE TABLE IF NOT EXISTS telefonia_config (
    id_telefonia_config  UUID PRIMARY KEY,
    enabled_telefonia    BOOLEAN      NOT NULL DEFAULT FALSE,
    ucm_url              VARCHAR(300),
    ucm_usuario          VARCHAR(120),
    ucm_clave_cifrada    TEXT,
    token_hash           VARCHAR(64),
    token_creado_at      TIMESTAMPTZ,
    puente_visto_at      TIMESTAMPTZ,
    puente_version       VARCHAR(40),
    puente_ip            VARCHAR(64),
    puente_ucm_ok        BOOLEAN,
    puente_ucm_error     VARCHAR(500),
    updated_by           UUID,
    updated_at_telefonia TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO telefonia_config (id_telefonia_config, enabled_telefonia)
VALUES ('00000000-0000-0000-0000-000000000007', FALSE)
ON CONFLICT (id_telefonia_config) DO NOTHING;
