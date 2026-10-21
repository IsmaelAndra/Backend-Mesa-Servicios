-- ============================================================================
-- Limpieza de los registros que dejaron las pruebas de QA en producción.
--
-- Es un borrado LÓGICO (deleted_* = TRUE / active = FALSE): no se pierde nada y
-- se puede deshacer cambiando el valor de vuelta. Va en una transacción: revisa
-- lo que muestran los SELECT del principio y termina con COMMIT (o ROLLBACK si
-- algo no cuadra).
--
-- Uso en el servidor:
--   docker compose exec -T db psql -U <usuario> -d <base> -f - < limpieza_registros_qa.sql
-- (o abrirlo en psql y ejecutarlo paso a paso).
-- ============================================================================

BEGIN;

-- 1) Qué se va a tocar. Revisa que solo salgan los registros de prueba.
SELECT 'ticket'      AS tabla, number_ticket::text AS clave, title_ticket AS nombre
  FROM ticket WHERE number_ticket IN (30, 31, 32) AND deleted_ticket = FALSE
UNION ALL
SELECT 'categoria', name_category, name_category
  FROM category WHERE name_category LIKE '[QA]%' AND deleted_category = FALSE
UNION ALL
SELECT 'area', name_area, name_area
  FROM area WHERE name_area LIKE '[QA]%' AND deleted_area = FALSE
UNION ALL
SELECT 'sede', code_sucursal, name_sucursal
  FROM sucursal WHERE name_sucursal LIKE '[QA]%' AND deleted_sucursal = FALSE
UNION ALL
SELECT 'politica_sla', name_sla_policy, name_sla_policy
  FROM sla_policy WHERE name_sla_policy LIKE '[QA]%' AND active_sla_policy = TRUE
UNION ALL
SELECT 'tipo_ticket', name_ticket_type, name_ticket_type
  FROM ticket_type WHERE name_ticket_type LIKE '[QA]%' AND deleted_ticket_type = FALSE
UNION ALL
SELECT 'activo', code_asset, name_asset
  FROM asset WHERE (code_asset = 'QA-LAP-001' OR name_asset LIKE '[QA]%') AND deleted_asset = FALSE;

-- 2) Borrado lógico.
UPDATE ticket      SET deleted_ticket = TRUE      WHERE number_ticket IN (30, 31, 32);
UPDATE category    SET deleted_category = TRUE    WHERE name_category LIKE '[QA]%';
UPDATE area        SET deleted_area = TRUE        WHERE name_area LIKE '[QA]%';
UPDATE sucursal    SET deleted_sucursal = TRUE    WHERE name_sucursal LIKE '[QA]%';
UPDATE ticket_type SET deleted_ticket_type = TRUE WHERE name_ticket_type LIKE '[QA]%';
UPDATE asset       SET deleted_asset = TRUE       WHERE code_asset = 'QA-LAP-001' OR name_asset LIKE '[QA]%';
-- La política SLA no tiene borrado lógico: se desactiva junto con sus reglas.
UPDATE sla_rule SET active_sla_rule = FALSE
 WHERE sla_policy_id IN (SELECT id_sla_policy FROM sla_policy WHERE name_sla_policy LIKE '[QA]%');
UPDATE sla_policy SET active_sla_policy = FALSE WHERE name_sla_policy LIKE '[QA]%';

-- 3) Si lo que salió en el paso 1 era lo esperado:
COMMIT;
-- Si no: ROLLBACK;
