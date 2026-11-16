-- ============================================================================
-- AVISOS ANTIGUOS CON EL NÚMERO DE TICKET DE ANTES
-- ============================================================================
-- Antes de V3/V4 los avisos guardaban el id numérico interno del ticket
-- ("Ticket #44"). V4 numeró los tickets de nuevo por orden de creación, así
-- que esos avisos ya no coinciden con el número que se ve en el tablero (#24).
-- Se reescribe el número de los avisos que apuntan a un ticket con el número
-- actual. Los avisos nuevos ya llevan number_ticket: reescribirlos no cambia nada.
-- ============================================================================

UPDATE notification n
SET subject_notification = regexp_replace(n.subject_notification, '([Tt]icket #?)[0-9]+', '\1' || t.number_ticket, 'g'),
    message_notification = regexp_replace(n.message_notification, '([Tt]icket #?)[0-9]+', '\1' || t.number_ticket, 'g')
FROM ticket t
WHERE n.ticket_id = t.id_ticket
  AND (n.subject_notification ~ '[Tt]icket #?[0-9]+' OR n.message_notification ~ '[Tt]icket #?[0-9]+');

-- ============================================================================
-- ESTADO DEL PROYECTO COHERENTE CON SU AVANCE
-- ============================================================================
-- Desde ahora terminar un proyecto exige todas sus tarjetas en HECHO, y el
-- primer movimiento del tablero lo pasa de PLANIFICADO a EN_CURSO
-- (ProyectoService.exigirTerminado / sincronizarEstado). Se corrigen los que ya
-- se contradecían ("Terminado" con 0 % o "Planificado" con trabajo hecho).

UPDATE proyecto p
SET estado_proyecto = 'EN_CURSO'
WHERE p.deleted_proyecto = FALSE
  AND p.estado_proyecto = 'TERMINADO'
  AND (NOT EXISTS (SELECT 1 FROM proyecto_item i WHERE i.proyecto_proyecto_item = p.id_proyecto AND i.deleted_proyecto_item = FALSE)
       OR EXISTS (SELECT 1 FROM proyecto_item i WHERE i.proyecto_proyecto_item = p.id_proyecto AND i.deleted_proyecto_item = FALSE
                  AND i.estado_proyecto_item <> 'HECHO'));

UPDATE proyecto p
SET estado_proyecto = 'EN_CURSO'
WHERE p.deleted_proyecto = FALSE
  AND p.estado_proyecto = 'PLANIFICADO'
  AND EXISTS (SELECT 1 FROM proyecto_item i WHERE i.proyecto_proyecto_item = p.id_proyecto AND i.deleted_proyecto_item = FALSE
              AND i.estado_proyecto_item <> 'POR_HACER');
