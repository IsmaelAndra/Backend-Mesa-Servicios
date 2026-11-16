-- =============================================================================
-- Roles de la mesa multi-área.
--
-- * Desaparece ADMIN: sus cuentas pasan a jefe de área (AREA_MANAGER) y el rol
--   queda dado de baja (no se borra: el historial y la auditoría lo nombran).
-- * Nuevo AUDITOR: consulta todo en solo lectura.
-- * area.manages_users_area: los jefes de esas áreas (Tecnología y Desarrollo)
--   pueden crear jefes, agentes y usuarios.
-- =============================================================================

INSERT INTO role (name_role, description_role)
VALUES ('AUDITOR', 'Auditor: consulta tickets, auditoría, activos y reportes en solo lectura')
ON CONFLICT (name_role) DO NOTHING;

UPDATE role
SET description_role = 'Jefe de área: gestiona los tickets, el equipo y la configuración de su área'
WHERE name_role = 'AREA_MANAGER';

UPDATE users
SET rol_user = (SELECT id_role FROM role WHERE name_role = 'AREA_MANAGER')
WHERE rol_user = (SELECT id_role FROM role WHERE name_role = 'ADMIN');

UPDATE role
SET deleted_role = TRUE,
    description_role = 'Rol retirado: sus cuentas pasaron a jefe de área (V27)'
WHERE name_role = 'ADMIN';

ALTER TABLE area ADD COLUMN IF NOT EXISTS manages_users_area BOOLEAN NOT NULL DEFAULT FALSE;
