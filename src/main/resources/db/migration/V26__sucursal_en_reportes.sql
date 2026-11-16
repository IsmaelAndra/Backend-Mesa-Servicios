-- =============================================================================
-- "Sede" pasa a llamarse "Sucursal" en toda la aplicación.
--
-- Los reportes de sistema se insertan con ON CONFLICT DO NOTHING en
-- R__datos_base.sql, así que en una base existente sus columnas seguían
-- diciendo "Sede". Aquí se cambian solo los de sistema, por su nombre interno
-- (que no cambia: REPORTE_SEDES sigue siendo la clave), y solo el texto
-- visible: alias de columna, "Sin sede", título y descripción.
-- =============================================================================

UPDATE report_config
SET query_sql = replace(replace(query_sql, 'AS "Sede"', 'AS "Sucursal"'), '''Sin sede''', '''Sin sucursal'''),
    title_report = replace(replace(title_report, 'sede', 'sucursal'), 'Sede', 'Sucursal'),
    description_report = replace(replace(replace(description_report, 'sedes', 'sucursales'), 'sede', 'sucursal'), 'Sede', 'Sucursal')
WHERE name_report IN ('REPORTE_TICKETS', 'REPORTE_SLA', 'REPORTE_AGENTES', 'REPORTE_CALIFICACIONES', 'REPORTE_SEDES', 'REPORTE_AREAS');
