-- 1) Búsqueda de tickets. TicketService.search filtra con lower(columna) LIKE, y
--    los índices de V1 estaban sobre la columna sin lower(): PostgreSQL no podía
--    usarlos y recorría la tabla entera en cada búsqueda.
DROP INDEX IF EXISTS idx_ticket_trgm_titulo;
DROP INDEX IF EXISTS idx_ticket_trgm_descripcion;
CREATE INDEX IF NOT EXISTS idx_ticket_trgm_titulo_lower
    ON ticket USING GIN (lower(title_ticket) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ticket_trgm_descripcion_lower
    ON ticket USING GIN (lower(description_ticket) gin_trgm_ops);

-- 2) Listas de tickets: siempre "no borrados, los más nuevos primero".
CREATE INDEX IF NOT EXISTS idx_ticket_vivos_recientes
    ON ticket (created_at_ticket DESC) WHERE deleted_ticket = FALSE;
CREATE INDEX IF NOT EXISTS idx_ticket_agente_recientes
    ON ticket (agent_ticket, created_at_ticket DESC) WHERE deleted_ticket = FALSE;
CREATE INDEX IF NOT EXISTS idx_ticket_solicitante_recientes
    ON ticket (user_ticket, created_at_ticket DESC) WHERE deleted_ticket = FALSE;
CREATE INDEX IF NOT EXISTS idx_ticket_area ON ticket (area_ticket);
CREATE INDEX IF NOT EXISTS idx_ticket_categoria ON ticket (category_ticket);

-- 3) Reportes del sistema: filtro por área, y sin tickets borrados (se contaban).
--    Solo los del sistema, por nombre; los que escribió un administrador no se tocan.
UPDATE report_config
   SET query_sql = replace(
         replace(query_sql, 'AND (:sucursalId IS NULL OR t.sucursal_id = :sucursalId)',
                 'AND (:sucursalId IS NULL OR t.sucursal_id = :sucursalId)
        AND (:areaId IS NULL OR t.area_ticket = :areaId)'),
         'WHERE (:desde IS NULL OR t.created_at_ticket >= :desde)',
         'WHERE t.deleted_ticket = FALSE
        AND (:desde IS NULL OR t.created_at_ticket >= :desde)')
 WHERE name_report IN ('REPORTE_TICKETS', 'REPORTE_SLA', 'REPORTE_AGENTES', 'REPORTE_CALIFICACIONES', 'REPORTE_SEDES')
   AND query_sql NOT LIKE '%:areaId%';

--    SLA y Calificaciones filtran por otra fecha, así que su condición es otra.
UPDATE report_config
   SET query_sql = replace(
         replace(query_sql,
                 'WHERE (:desde IS NULL OR ts.created_at >= :desde)',
                 'WHERE t.deleted_ticket = FALSE
        AND (:desde IS NULL OR ts.created_at >= :desde)'),
         'WHERE (:desde IS NULL OR r.rated_at >= :desde)',
         'WHERE t.deleted_ticket = FALSE
        AND (:desde IS NULL OR r.rated_at >= :desde)')
 WHERE name_report IN ('REPORTE_SLA', 'REPORTE_CALIFICACIONES')
   AND query_sql NOT LIKE '%deleted_ticket%';

-- 4) Reporte nuevo: cuánto trabajo recibe y resuelve cada área.
INSERT INTO report_config (name_report, title_report, description_report, query_sql, active_report,
                           icon_report, color_report) VALUES
('REPORTE_AREAS',
 'Rendimiento por área',
 'Volumen, pendientes, cumplimiento de SLA, tiempo de resolución y satisfacción de cada área.',
 'SELECT a.name_area AS "Área",
         COUNT(DISTINCT t.id_ticket) AS "Tickets",
         COUNT(DISTINCT t.id_ticket) FILTER (WHERE s.name_status NOT IN (''RESUELTO'',''CERRADO'')) AS "Pendientes",
         COUNT(DISTINCT t.id_ticket) FILTER (WHERE s.name_status IN (''RESUELTO'',''CERRADO'')) AS "Resueltos",
         COUNT(DISTINCT t.id_ticket) FILTER (WHERE ts.resolution_breached) AS "Fuera de SLA",
         ROUND((AVG(EXTRACT(EPOCH FROM (COALESCE(t.closing_date_ticket, ts.resolution_completed_at) - t.created_at_ticket)) / 3600)
               FILTER (WHERE s.name_status IN (''RESUELTO'',''CERRADO'')))::numeric, 1) AS "Resolución media (h)",
         ROUND(AVG(r.overall_satisfaction)::numeric, 2) AS "Satisfacción prom."
  FROM ticket t
  JOIN area a ON a.id_area = t.area_ticket
  LEFT JOIN status s ON s.id_status = t.status_ticket
  LEFT JOIN ticket_sla ts ON ts.ticket_id = t.id_ticket
  LEFT JOIN ticket_rating r ON r.ticket_id = t.id_ticket
  WHERE t.deleted_ticket = FALSE
    AND (:desde IS NULL OR t.created_at_ticket >= :desde)
    AND (:hasta IS NULL OR t.created_at_ticket < :hasta)
    AND (:tipoId IS NULL OR t.type_ticket = :tipoId)
    AND (:sucursalId IS NULL OR t.sucursal_id = :sucursalId)
    AND (:areaId IS NULL OR t.area_ticket = :areaId)
  GROUP BY a.name_area
  ORDER BY 2 DESC',
 TRUE, 'fa-solid fa-building', '#7C3AED')
ON CONFLICT (name_report) DO NOTHING;
