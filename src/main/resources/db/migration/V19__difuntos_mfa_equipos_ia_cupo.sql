-- ============================================================================
-- 1) Tema de temporada: Día de los Difuntos (1 y 2 de noviembre).
-- ============================================================================

INSERT INTO seasonal_theme (id_theme, code_theme, name_theme, start_month_theme, start_day_theme,
                            end_month_theme, end_day_theme, greeting_theme)
VALUES
  ('a0000000-0000-0000-0000-000000000007', 'DIFUNTOS', 'Día de los Difuntos', 11, 1, 11, 2,
   'Día de los Difuntos: recordamos con cariño a quienes ya no están. ¡Que no falte la colada morada y la guagua de pan!')
ON CONFLICT (code_theme) DO NOTHING;

-- ============================================================================
-- 2) MFA: equipos de confianza.
--
-- Tras confirmar el código, el equipo queda recordado 30 días (cookie HttpOnly
-- con un token; aquí solo su SHA-256). Durante ese tiempo el login no vuelve a
-- pedir el código en ese navegador. Cambiar la contraseña o desactivar el
-- doble factor olvida todos los equipos.
-- ============================================================================

CREATE TABLE IF NOT EXISTS mfa_trusted_device (
    id_trusted_device   UUID PRIMARY KEY,
    user_trusted_device UUID         NOT NULL,
    token_hash          VARCHAR(64)  NOT NULL,
    user_agent_device   VARCHAR(200),
    expires_at_device   TIMESTAMPTZ  NOT NULL,
    last_used_at_device TIMESTAMPTZ,
    created_at_device   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_mfa_trusted_device_token UNIQUE (token_hash),
    CONSTRAINT fk_mfa_trusted_device_user FOREIGN KEY (user_trusted_device) REFERENCES users (id_user) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_mfa_trusted_device_user ON mfa_trusted_device (user_trusted_device);

-- ============================================================================
-- 3) IA: cupo diario de consultas (pensado para la capa gratuita de GroqCloud).
--    daily_limit NULL o 0 = sin límite. ai_daily_usage cuenta las del día.
-- ============================================================================

ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS daily_limit INTEGER;
UPDATE ai_config SET daily_limit = 50 WHERE daily_limit IS NULL;

CREATE TABLE IF NOT EXISTS ai_daily_usage (
    day_usage   DATE PRIMARY KEY,
    count_usage INTEGER NOT NULL DEFAULT 0
);

-- Reglas de la institución para NEXA (solo si no hay unas escritas).
UPDATE ai_config
SET rules_text = 'Eres NEXA, la asistente de la Mesa de Servicios de TI de la COAC Magisterio de Pichincha (cooperativa de ahorro y crédito de docentes, Ecuador).
- Responde siempre en español neutro, con trato de usted, claro y breve.
- No inventes datos: si falta información del ticket, dilo y pide lo necesario.
- Nunca pidas ni repitas contraseñas, códigos de verificación, números de cuenta, cédulas completas ni datos de socios.
- Al clasificar la prioridad:
  CRITICA: caída general de un sistema core (core financiero, cajas, banca en línea), sin internet en una agencia o riesgo de seguridad o fraude.
  ALTA: un área o agencia no puede trabajar, falla el correo o un sistema para varias personas, o hay un plazo regulatorio cercano.
  MEDIA: una persona no puede trabajar con normalidad o hay una falla con alternativa temporal. Es la prioridad por defecto.
  BAJA: consultas, solicitudes de instalación o cambios que pueden esperar.
- Elige la categoría y el equipo solo entre los del catálogo; si ninguno encaja, déjalos vacíos.
- El resumen debe tener una o dos frases y decir qué falla, a quién afecta y desde cuándo, si se sabe.'
WHERE rules_text IS NULL OR btrim(rules_text) = '';
