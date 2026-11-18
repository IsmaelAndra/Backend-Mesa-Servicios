-- Dispositivos con sesión abierta (Mi cuenta → Dispositivos).
-- Cada inicio de sesión es una "sesión" que conserva su id aunque el refresh
-- token rote; se guarda el dispositivo (navegador y sistema, no el User-Agent
-- completo), la IP de la última renovación y cuándo se inició.
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS sesion_refresh_token uuid;
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS dispositivo_refresh_token VARCHAR(120);
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS ip_refresh_token VARCHAR(64);
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS inicio_refresh_token TIMESTAMPTZ;

-- Las sesiones anteriores: cada token vivo es su propia sesión.
UPDATE refresh_token SET sesion_refresh_token = id_refresh_token WHERE sesion_refresh_token IS NULL;
UPDATE refresh_token SET inicio_refresh_token = created_at WHERE inicio_refresh_token IS NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_user_sesion ON refresh_token (user_id, sesion_refresh_token);
