-- =====================================================================
-- V32: políticas SLA por área y reportes del modal con icono y color.
-- =====================================================================

-- Una política SLA puede ser global (NULL, la define el súper admin) o de un
-- área (la define su jefe). Las reglas de un área solo usan políticas
-- globales o de esa misma área.
ALTER TABLE sla_policy ADD COLUMN IF NOT EXISTS area_sla_policy uuid REFERENCES area (id_area);
CREATE INDEX IF NOT EXISTS idx_sla_policy_area ON sla_policy (area_sla_policy);

-- Los reportes del modal pasan a ser la única forma de crear reportes y
-- recuperan el icono y el color que tenían los reportes por SQL.
ALTER TABLE reporte_usuario ADD COLUMN IF NOT EXISTS icon_reporte_usuario varchar(100);
ALTER TABLE reporte_usuario ADD COLUMN IF NOT EXISTS color_reporte_usuario varchar(30);
