-- ============================================================
-- ACTIVOS (IT Assets) - Mesa de Servicios COAC
-- ============================================================
-- Tabla principal de activos
CREATE TABLE IF NOT EXISTS asset (
    id_asset                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_asset              VARCHAR(50) NOT NULL UNIQUE,
    name_asset              VARCHAR(150) NOT NULL,
    type_asset              VARCHAR(50) NOT NULL,
    brand_asset             VARCHAR(100),
    model_asset             VARCHAR(100),
    serial_number_asset     VARCHAR(100),
    status_asset            VARCHAR(30) NOT NULL DEFAULT 'DISPONIBLE',
    purchase_date_asset     TIMESTAMPTZ,
    provider_asset          VARCHAR(150),
    cost_asset              NUMERIC(12,2),
    warranty_until_asset    TIMESTAMPTZ,
    image_url_asset         VARCHAR(500),
    document_url_asset      VARCHAR(500),
    notes_asset             TEXT,
    assigned_user_id        UUID,
    area_id                 UUID,
    sucursal_id             UUID,
    created_at_asset        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_asset        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_asset           BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_asset_user     FOREIGN KEY (assigned_user_id) REFERENCES users (id_user) ON DELETE SET NULL,
    CONSTRAINT fk_asset_area     FOREIGN KEY (area_id)          REFERENCES area (id_area) ON DELETE SET NULL,
    CONSTRAINT fk_asset_sucursal FOREIGN KEY (sucursal_id)      REFERENCES sucursal (id_sucursal) ON DELETE SET NULL
);

-- Índices de rendimiento
CREATE INDEX IF NOT EXISTS idx_asset_code       ON asset (code_asset);
CREATE INDEX IF NOT EXISTS idx_asset_status     ON asset (status_asset);
CREATE INDEX IF NOT EXISTS idx_asset_type       ON asset (type_asset);
CREATE INDEX IF NOT EXISTS idx_asset_user       ON asset (assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_asset_area       ON asset (area_id);
CREATE INDEX IF NOT EXISTS idx_asset_sucursal   ON asset (sucursal_id);
CREATE INDEX IF NOT EXISTS idx_asset_serial     ON asset (serial_number_asset);

-- ============================================================
-- HISTORIAL DE ASIGNACIONES DE ACTIVOS
-- ============================================================
CREATE TABLE IF NOT EXISTS asset_history (
    id_asset_history        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id                UUID NOT NULL,
    user_id                 UUID NOT NULL,
    action_type             VARCHAR(30) NOT NULL,
    notes                   TEXT,
    created_at_asset_history TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ah_asset FOREIGN KEY (asset_id) REFERENCES asset (id_asset) ON DELETE CASCADE,
    CONSTRAINT fk_ah_user  FOREIGN KEY (user_id)  REFERENCES users (id_user) ON DELETE CASCADE
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_asset_history_asset ON asset_history (asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_history_user  ON asset_history (user_id);
CREATE INDEX IF NOT EXISTS idx_asset_history_created ON asset_history (created_at_asset_history DESC);

-- ============================================================
-- CHECK constraint para estados válidos
-- ============================================================
ALTER TABLE asset ADD CONSTRAINT chk_asset_status CHECK (status_asset IN (
    'DISPONIBLE', 'ASIGNADO', 'EN_REPARACION', 'BAJA'
));

-- CHECK constraint para tipos de acción en historial
ALTER TABLE asset_history ADD CONSTRAINT chk_ah_action CHECK (action_type IN (
    'ASIGNACION', 'REASIGNACION', 'DEVOLUCION', 'BAJA', 'REPARACION', 'FIN_REPARACION'
));

-- ============================================================
-- BÚSQUEDA GLOBAL (Fase 3) - Índice GIN para asset
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_asset_search_gin
    ON asset USING GIN (
        code_asset gin_trgm_ops,
        name_asset gin_trgm_ops,
        brand_asset gin_trgm_ops,
        model_asset gin_trgm_ops,
        serial_number_asset gin_trgm_ops,
        type_asset gin_trgm_ops
    );