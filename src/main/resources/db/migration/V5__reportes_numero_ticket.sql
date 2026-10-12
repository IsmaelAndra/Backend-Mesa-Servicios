-- ============================================================================
-- REPORTES DEL SISTEMA: NÚMERO DE TICKET EN VEZ DEL UUID
-- ============================================================================
-- Desde V3 la clave del ticket es un uuid, y los reportes del sistema seguían
-- mostrando "t.id_ticket" en la columna Ticket ("eea1790d-…") y ordenando por
-- él, que con uuid es un orden sin sentido. V4 añadió number_ticket, el número
-- que ve la gente.
--
-- R__datos_base.sql ya siembra el SQL corregido, pero solo para una base nueva:
-- sus INSERT usan ON CONFLICT DO NOTHING y sus UPDATE llevan guardas que, en
-- una base que ya tenía la sede, no vuelven a ejecutarse. Por eso el cambio
-- para las instalaciones existentes va aquí, una sola vez.
--
-- Solo se tocan los reportes del sistema, por nombre. Un reporte escrito por
-- un administrador es suyo y no se reescribe; si usa t.id_ticket, puede
-- cambiarlo desde el panel por t.number_ticket.
--
-- En una base nueva no afecta a ninguna fila: Flyway ejecuta las repetibles
-- después de las versionadas, y R__datos_base.sql inserta ya la versión buena.
-- ============================================================================

UPDATE report_config
SET query_sql = replace(
                    replace(query_sql, 't.id_ticket AS "Ticket"', 't.number_ticket AS "Ticket"'),
                    't.id_ticket DESC', 't.number_ticket DESC')
WHERE name_report IN ('REPORTE_TICKETS', 'REPORTE_SLA', 'REPORTE_CALIFICACIONES')
  AND query_sql LIKE '%t.id_ticket%';
