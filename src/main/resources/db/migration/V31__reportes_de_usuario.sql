-- =============================================================================
-- Reportes hechos desde el modal (Fase 2).
--
-- No guardan SQL: guardan la tabla, las columnas y los filtros elegidos, y el
-- backend arma la consulta con el catálogo cerrado (CatalogoReportes). El jefe
-- solo obtiene filas de sus áreas aunque el reporte lo haya hecho otro.
-- =============================================================================

CREATE TABLE IF NOT EXISTS reporte_usuario (
    id_reporte_usuario          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_reporte_usuario      VARCHAR(150) NOT NULL,
    descripcion_reporte_usuario VARCHAR(500),
    propietario_reporte_usuario UUID NOT NULL REFERENCES users (id_user),
    definicion_reporte_usuario  JSONB NOT NULL,
    created_at_reporte_usuario  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_reporte_usuario  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_reporte_usuario     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_reporte_usuario_propietario
    ON reporte_usuario (propietario_reporte_usuario) WHERE deleted_reporte_usuario = FALSE;
