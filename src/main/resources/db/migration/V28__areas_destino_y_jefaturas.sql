-- =============================================================================
-- Mesa multi-área: áreas que atienden, jefaturas y traslados.
--
-- 1. Área de tecnología: se usa la que ya exista ("Tecnología", "Tecnologías"
--    o "TI"; una llamada "TI" pasa a "Tecnología"). Solo se crea si no hay
--    ninguna. Igual con Desarrollo. Las dos gestionan usuarios (V27).
-- 2. Equipos y categorías pertenecen a un área. Hasta hoy todo era de TI, así
--    que los existentes quedan en Tecnología.
-- 3. ticket.area_destino_ticket: el área que ATIENDE el ticket. area_ticket
--    sigue siendo el área del solicitante. Los existentes van a Tecnología.
-- 4. area_jefe: qué áreas tiene a cargo cada jefe (una o varias). Se llena con
--    el área actual de cada jefe.
-- =============================================================================

UPDATE area
SET name_area = 'Tecnología',
    description_area = 'Soporte técnico, infraestructura y equipos'
WHERE upper(name_area) = 'TI'
  AND NOT EXISTS (SELECT 1 FROM area WHERE lower(name_area) LIKE 'tecnolog%');

INSERT INTO area (name_area, description_area, icon_area, color_area, priority_level, status_area)
SELECT 'Tecnología', 'Soporte técnico, infraestructura y equipos', 'fa-solid fa-desktop', '#2563EB', 5, TRUE
WHERE NOT EXISTS (SELECT 1 FROM area WHERE lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI');

INSERT INTO area (name_area, description_area, icon_area, color_area, priority_level, status_area)
SELECT 'Desarrollo', 'Desarrollo y mantenimiento de sistemas', 'fa-solid fa-code', '#7C3AED', 5, TRUE
WHERE NOT EXISTS (SELECT 1 FROM area WHERE lower(name_area) = 'desarrollo');

UPDATE area SET manages_users_area = TRUE
WHERE id_area = (SELECT id_area FROM area WHERE deleted_area = FALSE AND (lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI') ORDER BY CASE WHEN upper(name_area) = 'TI' THEN 1 ELSE 0 END, created_at_area LIMIT 1) OR lower(name_area) = 'desarrollo';

ALTER TABLE support_team ADD COLUMN IF NOT EXISTS area_team UUID;
ALTER TABLE category ADD COLUMN IF NOT EXISTS area_category UUID;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS area_destino_ticket UUID;

DO 'BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ''fk_team_area'') THEN
    ALTER TABLE support_team ADD CONSTRAINT fk_team_area FOREIGN KEY (area_team) REFERENCES area (id_area);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ''fk_category_area'') THEN
    ALTER TABLE category ADD CONSTRAINT fk_category_area FOREIGN KEY (area_category) REFERENCES area (id_area);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ''fk_ticket_area_destino'') THEN
    ALTER TABLE ticket ADD CONSTRAINT fk_ticket_area_destino FOREIGN KEY (area_destino_ticket) REFERENCES area (id_area);
  END IF;
END';

UPDATE support_team
SET area_team = (SELECT id_area FROM area WHERE deleted_area = FALSE AND (lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI') ORDER BY CASE WHEN upper(name_area) = 'TI' THEN 1 ELSE 0 END, created_at_area LIMIT 1)
WHERE area_team IS NULL;

UPDATE category
SET area_category = COALESCE(
        (SELECT t.area_team FROM support_team t WHERE t.id_team = category.team_category),
        (SELECT id_area FROM area WHERE deleted_area = FALSE AND (lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI') ORDER BY CASE WHEN upper(name_area) = 'TI' THEN 1 ELSE 0 END, created_at_area LIMIT 1))
WHERE area_category IS NULL;

UPDATE ticket
SET area_destino_ticket = COALESCE(
        (SELECT c.area_category FROM category c WHERE c.id_category = ticket.category_ticket),
        (SELECT id_area FROM area WHERE deleted_area = FALSE AND (lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI') ORDER BY CASE WHEN upper(name_area) = 'TI' THEN 1 ELSE 0 END, created_at_area LIMIT 1))
WHERE area_destino_ticket IS NULL;

ALTER TABLE ticket ALTER COLUMN area_destino_ticket SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_area_destino ON ticket (area_destino_ticket) WHERE deleted_ticket = FALSE;
CREATE INDEX IF NOT EXISTS idx_team_area ON support_team (area_team);
CREATE INDEX IF NOT EXISTS idx_category_area ON category (area_category);

CREATE TABLE IF NOT EXISTS area_jefe (
    id_area_jefe          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    area_area_jefe        UUID NOT NULL REFERENCES area (id_area),
    user_area_jefe        UUID NOT NULL REFERENCES users (id_user),
    created_at_area_jefe  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_area_jefe UNIQUE (area_area_jefe, user_area_jefe)
);
CREATE INDEX IF NOT EXISTS idx_area_jefe_user ON area_jefe (user_area_jefe);

INSERT INTO area_jefe (area_area_jefe, user_area_jefe)
SELECT u.area_id, u.id_user
FROM users u
JOIN role r ON r.id_role = u.rol_user
WHERE r.name_role = 'AREA_MANAGER' AND u.deleted_user = FALSE AND u.area_id IS NOT NULL
ON CONFLICT (area_area_jefe, user_area_jefe) DO NOTHING;
