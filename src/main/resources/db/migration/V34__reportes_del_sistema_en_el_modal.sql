-- =====================================================================
-- V34: los reportes del sistema se rehacen con el modal (catálogo cerrado)
-- y desaparecen los reportes escritos en SQL (report_config).
--
-- Un reporte del sistema no tiene dueño: lo ven todos los que ven reportes
-- (el jefe, acotado a su área) y solo lo edita o borra el súper admin.
-- Las definiciones usan las claves de CatalogoReportes; si se renombra una
-- columna allí, hay que actualizar también estas filas.
-- =====================================================================

ALTER TABLE reporte_usuario ADD COLUMN IF NOT EXISTS sistema_reporte_usuario boolean NOT NULL DEFAULT FALSE;
ALTER TABLE reporte_usuario ALTER COLUMN propietario_reporte_usuario DROP NOT NULL;

INSERT INTO reporte_usuario (nombre_reporte_usuario, descripcion_reporte_usuario, icon_reporte_usuario,
                             color_reporte_usuario, definicion_reporte_usuario, sistema_reporte_usuario,
                             created_at_reporte_usuario, updated_at_reporte_usuario, deleted_reporte_usuario)
SELECT v.nombre, v.descripcion, v.icono, v.color, v.definicion::jsonb, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM (VALUES
    ('Detalle de tickets', 'Cada ticket con su solicitante, agente, área, estado y fechas.',
     'fa-solid fa-list-check', '#062E86',
     '{"tabla":"TICKETS","columnas":["numero","titulo","solicitante","sucursal","agente","area_atiende","categoria","prioridad","estado","creado","cerrado"],"filtros":{},"medidas":[]}'),
    ('Rendimiento de agentes', 'Tickets, pendientes, resueltos, fuera de SLA y satisfacción por agente.',
     'fa-solid fa-users', '#0F766E',
     '{"tabla":"TICKETS","columnas":["agente"],"filtros":{},"medidas":["total","abiertos","resueltos","sla_vencidos","satisfaccion"]}'),
    ('Rendimiento por área', 'Lo que atiende cada área: volumen, pendientes, SLA, tiempo de resolución y satisfacción.',
     'fa-solid fa-building', '#7C3AED',
     '{"tabla":"TICKETS","columnas":["area_atiende"],"filtros":{},"medidas":["total","abiertos","resueltos","sla_vencidos","horas_promedio","satisfaccion"]}'),
    ('Rendimiento por sucursal', 'Tickets, pendientes, resueltos, fuera de SLA y satisfacción por sucursal.',
     'fa-solid fa-store', '#0891B2',
     '{"tabla":"TICKETS","columnas":["sucursal"],"filtros":{},"medidas":["total","abiertos","resueltos","sla_vencidos","satisfaccion"]}'),
    ('Calificaciones recibidas', 'Cada calificación con sus criterios y comentarios.',
     'fa-solid fa-star', '#D97706',
     '{"tabla":"CALIFICACIONES","columnas":["numero","titulo","fecha","solicitante","sucursal","agente","requisitos","tiempo_respuesta","calidad","comunicacion","satisfaccion","comentarios"],"filtros":{},"medidas":[]}'),
    ('Cumplimiento de SLA', 'Cada ticket con su política y si respondió y resolvió a tiempo.',
     'fa-solid fa-stopwatch', '#B45309',
     '{"tabla":"TICKETS","columnas":["numero","titulo","sucursal","area_atiende","prioridad","politica_sla","sla_respuesta","sla_resolucion","vence_resolucion","cerrado"],"filtros":{},"medidas":[]}')
) AS v(nombre, descripcion, icono, color, definicion)
WHERE NOT EXISTS (
    SELECT 1 FROM reporte_usuario r
    WHERE r.sistema_reporte_usuario AND r.nombre_reporte_usuario = v.nombre
);

-- Adiós a los reportes por SQL (no hay claves foráneas hacia report_config).
DELETE FROM report_config;
