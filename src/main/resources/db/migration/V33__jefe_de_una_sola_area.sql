-- =====================================================================
-- V33: un jefe responde por UNA sola área, la de su cuenta (users.area_id).
-- La V28 permitía varias en area_jefe; se retira la tabla.
-- =====================================================================

-- Si un jefe tenía exactamente un área a cargo distinta de la suya, esa pasa
-- a ser su área (es lo que se le había asignado a mano).
UPDATE users u
SET area_id = aj.area_area_jefe
FROM area_jefe aj
JOIN role r ON r.name_role = 'AREA_MANAGER'
WHERE aj.user_area_jefe = u.id_user
  AND u.rol_user = r.id_role
  AND u.area_id IS DISTINCT FROM aj.area_area_jefe
  AND (SELECT count(*) FROM area_jefe x WHERE x.user_area_jefe = u.id_user) = 1;

DROP TABLE IF EXISTS area_jefe;
