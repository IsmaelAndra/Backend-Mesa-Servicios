-- ============================================================================
-- NOMBRES DE REGISTROS ELIMINADOS, LIBRES PARA VOLVER A USARSE
-- ============================================================================
-- Áreas, categorías y tipos de ticket se borran de forma lógica, pero su nombre
-- es UNIQUE en la tabla: el nombre de algo eliminado no se podía volver a usar
-- (crear "Atención" otra vez daba error 500). Desde ahora AdminService le
-- añade " (eliminado <fecha>)" al borrar; aquí se hace con los que ya estaban.
-- ============================================================================

UPDATE area
SET name_area = left(name_area, 75) || ' (eliminado ' || to_char(updated_at_area, 'YYYYMMDDHH24MISS') || ')'
WHERE deleted_area = TRUE AND name_area NOT LIKE '% (eliminado %';

UPDATE category
SET name_category = left(name_category, 75) || ' (eliminado ' || to_char(updated_at_category, 'YYYYMMDDHH24MISS') || ')'
WHERE deleted_category = TRUE AND name_category NOT LIKE '% (eliminado %';

UPDATE ticket_type
SET name_ticket_type = left(name_ticket_type, 75) || ' (eliminado ' || to_char(updated_at_ticket_type, 'YYYYMMDDHH24MISS') || ')'
WHERE deleted_ticket_type = TRUE AND name_ticket_type NOT LIKE '% (eliminado %';

-- Personas que quedaron en un área eliminada antes de que el borrado exigiera
-- reasignarlas: pasan al área de tecnología (se busca, nunca por nombre exacto).
UPDATE users
SET area_id = (SELECT id_area FROM area
               WHERE deleted_area = FALSE AND (lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI')
               ORDER BY CASE WHEN upper(name_area) = 'TI' THEN 1 ELSE 0 END, created_at_area LIMIT 1)
WHERE deleted_user = FALSE
  AND area_id IN (SELECT id_area FROM area WHERE deleted_area = TRUE)
  AND EXISTS (SELECT 1 FROM area WHERE deleted_area = FALSE AND (lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI'));

-- El asistente se llama MAPI (V22/V23): quedaba "NEXA" en la descripción de
-- alguna automatización y se veía en la lista.
UPDATE workflow
SET description_workflow = regexp_replace(description_workflow, '\mNEXA\M', 'MAPI', 'g')
WHERE description_workflow ~ '\mNEXA\M';
UPDATE workflow
SET name_workflow = regexp_replace(name_workflow, '\mNEXA\M', 'MAPI', 'g')
WHERE name_workflow ~ '\mNEXA\M';
