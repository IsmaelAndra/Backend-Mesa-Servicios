-- =====================================================================
-- V35 (Fase 3): aviso antes del plazo de una actividad y proyectos.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Recordatorio de actividades: cuántos minutos antes del plazo se avisa
--    (NULL = sin aviso) y cuándo se envió (para no repetirlo).
-- ---------------------------------------------------------------------
ALTER TABLE task ADD COLUMN IF NOT EXISTS recordatorio_minutos_task integer;
ALTER TABLE task ADD COLUMN IF NOT EXISTS recordatorio_enviado_task timestamptz;
CREATE INDEX IF NOT EXISTS idx_task_recordatorio ON task (due_task)
    WHERE recordatorio_minutos_task IS NOT NULL AND recordatorio_enviado_task IS NULL AND deleted_task = FALSE;

-- ---------------------------------------------------------------------
-- 2. Proyectos. Son de UN área (la del jefe que lo crea); otras áreas
--    participan solo si su jefe acepta la invitación. Los equipos de
--    proyecto (miembros) son distintos de los equipos de atención de tickets.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proyecto (
    id_proyecto            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_proyecto        VARCHAR(150) NOT NULL,
    descripcion_proyecto   TEXT,
    area_proyecto          UUID NOT NULL REFERENCES area (id_area),
    estado_proyecto        VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO'
        CHECK (estado_proyecto IN ('PLANIFICADO', 'EN_CURSO', 'PAUSADO', 'TERMINADO', 'CANCELADO')),
    icon_proyecto          VARCHAR(100),
    color_proyecto         VARCHAR(30),
    inicio_proyecto        DATE,
    fin_proyecto           DATE,
    creado_por_proyecto    UUID REFERENCES users (id_user),
    created_at_proyecto    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_proyecto    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_proyecto       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_proyecto_area ON proyecto (area_proyecto) WHERE deleted_proyecto = FALSE;

-- Áreas invitadas: PENDIENTE hasta que su jefe acepte o rechace.
CREATE TABLE IF NOT EXISTS proyecto_area (
    id_proyecto_area          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_proyecto_area    UUID NOT NULL REFERENCES proyecto (id_proyecto),
    area_proyecto_area        UUID NOT NULL REFERENCES area (id_area),
    estado_proyecto_area      VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado_proyecto_area IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA')),
    invitado_por_proyecto_area UUID REFERENCES users (id_user),
    decidido_por_proyecto_area UUID REFERENCES users (id_user),
    decidido_at_proyecto_area TIMESTAMPTZ,
    motivo_proyecto_area      VARCHAR(500),
    created_at_proyecto_area  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_proyecto_area UNIQUE (proyecto_proyecto_area, area_proyecto_area)
);
CREATE INDEX IF NOT EXISTS idx_proyecto_area_area ON proyecto_area (area_proyecto_area, estado_proyecto_area);

-- Equipo del proyecto.
CREATE TABLE IF NOT EXISTS proyecto_miembro (
    id_proyecto_miembro         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_proyecto_miembro   UUID NOT NULL REFERENCES proyecto (id_proyecto),
    user_proyecto_miembro       UUID NOT NULL REFERENCES users (id_user),
    rol_proyecto_miembro        VARCHAR(20) NOT NULL DEFAULT 'MIEMBRO'
        CHECK (rol_proyecto_miembro IN ('RESPONSABLE', 'MIEMBRO')),
    created_at_proyecto_miembro TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_proyecto_miembro UNIQUE (proyecto_proyecto_miembro, user_proyecto_miembro)
);
CREATE INDEX IF NOT EXISTS idx_proyecto_miembro_user ON proyecto_miembro (user_proyecto_miembro);

CREATE TABLE IF NOT EXISTS proyecto_sprint (
    id_proyecto_sprint         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_proyecto_sprint   UUID NOT NULL REFERENCES proyecto (id_proyecto),
    nombre_proyecto_sprint     VARCHAR(120) NOT NULL,
    objetivo_proyecto_sprint   VARCHAR(500),
    inicio_proyecto_sprint     DATE,
    fin_proyecto_sprint        DATE,
    estado_proyecto_sprint     VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO'
        CHECK (estado_proyecto_sprint IN ('PLANIFICADO', 'ACTIVO', 'CERRADO')),
    created_at_proyecto_sprint TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_proyecto_sprint    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_proyecto_sprint ON proyecto_sprint (proyecto_proyecto_sprint);

-- Tarjetas del tablero. El avance del proyecto es la suma de los pesos de
-- las tarjetas HECHO sobre el total.
CREATE TABLE IF NOT EXISTS proyecto_item (
    id_proyecto_item          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_proyecto_item    UUID NOT NULL REFERENCES proyecto (id_proyecto),
    sprint_proyecto_item      UUID REFERENCES proyecto_sprint (id_proyecto_sprint),
    titulo_proyecto_item      VARCHAR(200) NOT NULL,
    descripcion_proyecto_item TEXT,
    estado_proyecto_item      VARCHAR(20) NOT NULL DEFAULT 'POR_HACER'
        CHECK (estado_proyecto_item IN ('POR_HACER', 'EN_CURSO', 'EN_REVISION', 'HECHO')),
    peso_proyecto_item        INTEGER NOT NULL DEFAULT 1 CHECK (peso_proyecto_item BETWEEN 1 AND 100),
    responsable_proyecto_item UUID REFERENCES users (id_user),
    limite_proyecto_item      DATE,
    orden_proyecto_item       INTEGER NOT NULL DEFAULT 0,
    creado_por_proyecto_item  UUID REFERENCES users (id_user),
    created_at_proyecto_item  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_proyecto_item  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_proyecto_item     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_proyecto_item ON proyecto_item (proyecto_proyecto_item) WHERE deleted_proyecto_item = FALSE;

-- Objetivos del proyecto; un objetivo cumplido es un logro.
CREATE TABLE IF NOT EXISTS proyecto_objetivo (
    id_proyecto_objetivo          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_proyecto_objetivo    UUID NOT NULL REFERENCES proyecto (id_proyecto),
    descripcion_proyecto_objetivo VARCHAR(500) NOT NULL,
    logrado_at_proyecto_objetivo  TIMESTAMPTZ,
    nota_logro_proyecto_objetivo  VARCHAR(1000),
    orden_proyecto_objetivo       INTEGER NOT NULL DEFAULT 0,
    created_at_proyecto_objetivo  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_proyecto_objetivo     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_proyecto_objetivo ON proyecto_objetivo (proyecto_proyecto_objetivo);
