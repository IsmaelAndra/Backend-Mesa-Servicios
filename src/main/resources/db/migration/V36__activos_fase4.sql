-- =====================================================================
-- V36 (Fase 4): activos de Tecnología y Desarrollo, actas FOR-POL-TIC-01-10.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Qué áreas gestionan activos. Arranca igual que manages_users_area
--    (Tecnología y Desarrollo), pero es un permiso aparte.
-- ---------------------------------------------------------------------
ALTER TABLE area ADD COLUMN IF NOT EXISTS gestiona_activos_area BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE area SET gestiona_activos_area = TRUE
WHERE manages_users_area = TRUE OR lower(name_area) LIKE 'tecnolog%' OR lower(name_area) LIKE 'desarroll%' OR upper(name_area) = 'TI';

-- Cargo de la persona: sale en el acta (custodio, quien entrega, revisa...).
ALTER TABLE users ADD COLUMN IF NOT EXISTS cargo_user VARCHAR(150);

-- ---------------------------------------------------------------------
-- 2. Maestro de tipos de activo. El prefijo entra en el código (TI-LAP-000123).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_type (
    id_asset_type          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_asset_type        VARCHAR(80) NOT NULL,
    prefix_asset_type      VARCHAR(10) NOT NULL,
    icon_asset_type        VARCHAR(100),
    color_asset_type       VARCHAR(30),
    active_asset_type      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_asset_type  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_asset_type  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_asset_type     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_type_nombre ON asset_type (lower(name_asset_type)) WHERE deleted_asset_type = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_type_prefijo ON asset_type (upper(prefix_asset_type)) WHERE deleted_asset_type = FALSE;

INSERT INTO asset_type (name_asset_type, prefix_asset_type, icon_asset_type, color_asset_type)
SELECT v.n, v.p, v.i, v.c
FROM (VALUES
    ('Laptop', 'LAP', 'fa-solid fa-laptop', '#2563EB'),
    ('Computador de escritorio', 'CPU', 'fa-solid fa-desktop', '#0891B2'),
    ('Monitor', 'MON', 'fa-solid fa-tv', '#0F766E'),
    ('Impresora', 'IMP', 'fa-solid fa-print', '#7C3AED'),
    ('Escáner', 'ESC', 'fa-solid fa-file-image', '#9333EA'),
    ('Teclado', 'TEC', 'fa-solid fa-keyboard', '#64748B'),
    ('Mouse', 'MOU', 'fa-solid fa-computer-mouse', '#64748B'),
    ('Teléfono IP', 'TEL', 'fa-solid fa-phone', '#16A34A'),
    ('Celular', 'CEL', 'fa-solid fa-mobile-screen', '#16A34A'),
    ('Servidor', 'SRV', 'fa-solid fa-server', '#DC2626'),
    ('Equipo de red', 'RED', 'fa-solid fa-network-wired', '#D97706'),
    ('UPS', 'UPS', 'fa-solid fa-car-battery', '#B45309'),
    ('Proyector', 'PRY', 'fa-solid fa-video', '#DB2777'),
    ('Otro', 'OTR', 'fa-solid fa-box', '#475569')
) AS v(n, p, i, c)
WHERE NOT EXISTS (SELECT 1 FROM asset_type t WHERE lower(t.name_asset_type) = lower(v.n) AND t.deleted_asset_type = FALSE);

-- Tipos escritos a mano en activos existentes que no estén en el maestro.
INSERT INTO asset_type (name_asset_type, prefix_asset_type, icon_asset_type, color_asset_type)
SELECT DISTINCT initcap(lower(a.type_asset)), upper(left(regexp_replace(a.type_asset, '[^A-Za-z]', '', 'g') || 'XXX', 3)) || 'X',
       'fa-solid fa-box', '#475569'
FROM asset a
WHERE a.type_asset IS NOT NULL AND a.type_asset <> ''
  AND NOT EXISTS (SELECT 1 FROM asset_type t WHERE lower(t.name_asset_type) = lower(a.type_asset)
                  OR upper(t.prefix_asset_type) = upper(a.type_asset))
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Campos nuevos del activo.
-- ---------------------------------------------------------------------
ALTER TABLE asset ADD COLUMN IF NOT EXISTS asset_type_asset UUID REFERENCES asset_type (id_asset_type);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS description_asset TEXT;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS ubicacion_asset VARCHAR(300);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS accesorios_asset TEXT;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS estado_fisico_asset VARCHAR(20) NOT NULL DEFAULT 'BUENO'
    CHECK (estado_fisico_asset IN ('NUEVO', 'BUENO', 'REGULAR', 'CON_NOVEDADES'));
ALTER TABLE asset ADD COLUMN IF NOT EXISTS configuracion_asset TEXT;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS novedades_asset TEXT;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS garantia_avisada_asset TIMESTAMPTZ;

UPDATE asset a SET asset_type_asset = t.id_asset_type
FROM asset_type t
WHERE a.asset_type_asset IS NULL AND t.deleted_asset_type = FALSE
  AND (lower(t.name_asset_type) = lower(a.type_asset) OR upper(t.prefix_asset_type) = upper(a.type_asset));

-- Sin persona, un activo está "En bodega" (antes DISPONIBLE).
ALTER TABLE asset DROP CONSTRAINT IF EXISTS chk_asset_status;
UPDATE asset SET status_asset = 'EN_BODEGA' WHERE status_asset = 'DISPONIBLE';
ALTER TABLE asset ADD CONSTRAINT chk_asset_status
    CHECK (status_asset IN ('EN_BODEGA', 'ASIGNADO', 'EN_REPARACION', 'BAJA'));

CREATE INDEX IF NOT EXISTS idx_asset_tipo ON asset (asset_type_asset) WHERE deleted_asset = FALSE;
CREATE INDEX IF NOT EXISTS idx_asset_estado ON asset (status_asset) WHERE deleted_asset = FALSE;
CREATE INDEX IF NOT EXISTS idx_asset_asignado ON asset (assigned_user_id) WHERE deleted_asset = FALSE;
CREATE INDEX IF NOT EXISTS idx_asset_garantia ON asset (warranty_until_asset)
    WHERE deleted_asset = FALSE AND garantia_avisada_asset IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_codigo_lower ON asset (lower(code_asset));

-- ---------------------------------------------------------------------
-- 4. Fotos y documentos del activo (nunca URL: archivos en adjuntos/).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_archivo (
    id_asset_archivo          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_asset_archivo       UUID NOT NULL REFERENCES asset (id_asset),
    tipo_asset_archivo        VARCHAR(20) NOT NULL
        CHECK (tipo_asset_archivo IN ('FOTO', 'GARANTIA', 'FACTURA', 'OTRO')),
    nombre_asset_archivo      VARCHAR(255) NOT NULL,
    ruta_asset_archivo        VARCHAR(500) NOT NULL,
    mime_asset_archivo        VARCHAR(100) NOT NULL,
    tamano_asset_archivo      BIGINT NOT NULL,
    vence_asset_archivo       DATE,
    subido_por_asset_archivo  UUID REFERENCES users (id_user),
    created_at_asset_archivo  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_asset_archivo     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_asset_archivo ON asset_archivo (asset_asset_archivo) WHERE deleted_asset_archivo = FALSE;

-- ---------------------------------------------------------------------
-- 5. Configuración (fila única) y contadores de numeración.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_config (
    id_asset_config            UUID PRIMARY KEY,
    formato_codigo_config      VARCHAR(60) NOT NULL DEFAULT 'TI-{TIPO}-{NUM:6}',
    contador_por_tipo_config   BOOLEAN NOT NULL DEFAULT TRUE,
    formato_acta_config        VARCHAR(60) NOT NULL DEFAULT 'ACTA-{ANIO}-{NUM:4}',
    ciudad_config              VARCHAR(80) NOT NULL DEFAULT 'Quito',
    codigo_formulario_config   VARCHAR(40) NOT NULL DEFAULT 'FOR-POL-TIC-01-10',
    version_formulario_config  VARCHAR(20) NOT NULL DEFAULT 'V01.00',
    dias_aviso_garantia_config INTEGER NOT NULL DEFAULT 30,
    updated_at_asset_config    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO asset_config (id_asset_config) VALUES ('00000000-0000-0000-0000-00000000a5e7')
ON CONFLICT DO NOTHING;

-- Siguiente número por clave ('ACTIVO', 'ACTIVO:LAP', 'ACTA:2026'). Se reserva
-- con UPDATE ... RETURNING para que dos altas a la vez no repitan número.
CREATE TABLE IF NOT EXISTS asset_contador (
    clave_asset_contador  VARCHAR(60) PRIMARY KEY,
    valor_asset_contador  BIGINT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------
-- 6. Actas de entrega, devolución y reasignación (FOR-POL-TIC-01-10).
--    Cada fila de equipo guarda una FOTO de los datos al momento del acta:
--    si el activo cambia después, el acta impresa no cambia.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS acta_activo (
    id_acta_activo              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_acta_activo          VARCHAR(40) NOT NULL UNIQUE,
    tipo_acta_activo            VARCHAR(20) NOT NULL
        CHECK (tipo_acta_activo IN ('ENTREGA', 'DEVOLUCION', 'REASIGNACION')),
    estado_acta_activo          VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado_acta_activo IN ('PENDIENTE', 'ACEPTADA', 'FIRMADA', 'ANULADA')),
    fecha_acta_activo           DATE NOT NULL,
    ciudad_acta_activo          VARCHAR(80) NOT NULL,
    asunto_acta_activo          VARCHAR(300),
    cc_acta_activo              VARCHAR(300),
    custodio_acta_activo        UUID NOT NULL REFERENCES users (id_user),
    custodio_anterior_acta_activo UUID REFERENCES users (id_user),
    entregado_por_acta_activo   UUID REFERENCES users (id_user),
    recibido_por_acta_activo    UUID REFERENCES users (id_user),
    autorizado_por_acta_activo  UUID REFERENCES users (id_user),
    revisado_por_acta_activo    UUID REFERENCES users (id_user),
    sucursal_acta_activo        UUID REFERENCES sucursal (id_sucursal),
    observaciones_acta_activo   TEXT,
    aceptada_at_acta_activo     TIMESTAMPTZ,
    aceptada_ip_acta_activo     VARCHAR(64),
    firmada_ruta_acta_activo    VARCHAR(500),
    firmada_at_acta_activo      TIMESTAMPTZ,
    creada_por_acta_activo      UUID REFERENCES users (id_user),
    created_at_acta_activo      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_acta_custodio ON acta_activo (custodio_acta_activo, estado_acta_activo);
CREATE INDEX IF NOT EXISTS idx_acta_fecha ON acta_activo (created_at_acta_activo);

CREATE TABLE IF NOT EXISTS acta_activo_item (
    id_acta_activo_item        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    acta_acta_activo_item      UUID NOT NULL REFERENCES acta_activo (id_acta_activo),
    asset_acta_activo_item     UUID NOT NULL REFERENCES asset (id_asset),
    principal_acta_activo_item BOOLEAN NOT NULL DEFAULT FALSE,
    orden_acta_activo_item     INTEGER NOT NULL DEFAULT 0,
    codigo_acta_activo_item    VARCHAR(50) NOT NULL,
    tipo_acta_activo_item      VARCHAR(80),
    nombre_acta_activo_item    VARCHAR(150),
    marca_modelo_acta_activo_item VARCHAR(210),
    serie_acta_activo_item     VARCHAR(100),
    estado_fisico_acta_activo_item VARCHAR(20),
    accesorios_acta_activo_item TEXT,
    configuracion_acta_activo_item TEXT,
    novedades_acta_activo_item TEXT
);
CREATE INDEX IF NOT EXISTS idx_acta_item_acta ON acta_activo_item (acta_acta_activo_item);
CREATE INDEX IF NOT EXISTS idx_acta_item_asset ON acta_activo_item (asset_acta_activo_item);
