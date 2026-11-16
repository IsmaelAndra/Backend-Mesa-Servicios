-- ============================================================================
-- UN PROYECTO TERMINADO TIENE EL 100 % DE AVANCE
-- ============================================================================
-- El avance del proyecto es la suma del peso de las tarjetas HECHO (los pesos
-- son % del proyecto). Terminarlo exige ahora llegar al 100 %
-- (ProyectoService.exigirTerminado). Los que ya estaban "Terminado" por debajo
-- vuelven a "En curso".
-- ============================================================================

UPDATE proyecto p
SET estado_proyecto = 'EN_CURSO'
WHERE p.deleted_proyecto = FALSE
  AND p.estado_proyecto = 'TERMINADO'
  AND COALESCE((SELECT sum(i.peso_proyecto_item) FROM proyecto_item i
                WHERE i.proyecto_proyecto_item = p.id_proyecto AND i.deleted_proyecto_item = FALSE
                  AND i.estado_proyecto_item = 'HECHO'), 0) < 100;
