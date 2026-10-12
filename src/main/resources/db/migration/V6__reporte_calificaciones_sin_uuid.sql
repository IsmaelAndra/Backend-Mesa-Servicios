-- ============================================================================
-- REPORTE DE CALIFICACIONES: SIN LA COLUMNA "EVALUACIÓN"
-- ============================================================================
-- La primera columna era r.id_rating, que desde V3 es un uuid: no le dice nada
-- a quien lee el reporte y ocupaba el sitio más visible. El ticket ya identifica
-- cada evaluación (un ticket se califica una vez). El desempate del orden pasa
-- a la fecha de la evaluación, que sí tiene sentido.
--
-- Igual que V5: R__datos_base.sql ya trae el SQL bueno para una base nueva, y
-- esto corrige una vez las existentes. Solo el reporte del sistema; uno escrito
-- por un administrador no se reescribe.
-- ============================================================================

UPDATE report_config
SET query_sql = replace(
                    regexp_replace(query_sql, 'r\.id_rating AS "Evaluación",\s*', '', 'g'),
                    'r.id_rating DESC', 'r.rated_at DESC')
WHERE name_report = 'REPORTE_CALIFICACIONES'
  AND query_sql LIKE '%r.id_rating%';
