-- Equipo que incluye automáticamente a todos los agentes activos (también a
-- los que se creen después). Así un equipo nuevo reparte tickets sin tener que
-- añadir a cada agente a mano; quien se quite del equipo no vuelve a entrar.
ALTER TABLE support_team ADD COLUMN IF NOT EXISTS auto_members_team BOOLEAN NOT NULL DEFAULT FALSE;
