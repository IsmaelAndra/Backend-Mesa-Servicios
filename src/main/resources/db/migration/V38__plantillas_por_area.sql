-- Plantillas de correo por área (V38). Cada área puede tener su propia versión
-- (asunto y cuerpo) de las plantillas de los avisos de tickets; si no la tiene,
-- se usa la general de notification_template. Las edita el jefe de esa área o
-- el súper admin. El destinatario sigue siendo el de la plantilla general.
CREATE TABLE IF NOT EXISTS plantilla_area (
    id_plantilla_area          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_plantilla_area      VARCHAR(100) NOT NULL,
    area_plantilla_area        UUID NOT NULL REFERENCES area (id_area),
    asunto_plantilla_area      VARCHAR(255) NOT NULL,
    cuerpo_plantilla_area      TEXT NOT NULL,
    activa_plantilla_area      BOOLEAN NOT NULL DEFAULT TRUE,
    editada_por_plantilla_area UUID REFERENCES users (id_user),
    created_at_plantilla_area  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_plantilla_area  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plantilla_area UNIQUE (nombre_plantilla_area, area_plantilla_area)
);

CREATE INDEX IF NOT EXISTS idx_plantilla_area_area ON plantilla_area (area_plantilla_area);

-- El icono del asistente: la pantalla de IA ponía sola el símbolo del proveedor
-- (⚡, ✨...) al elegirlo, y el botón dejaba de mostrar a MAPI sin que nadie lo
-- pidiera. Solo se limpian esos símbolos automáticos; un icono elegido a mano
-- (clase, imagen u otro emoji) se respeta.
UPDATE ai_config SET provider_icon = NULL
WHERE provider_icon IN ('⚡', '✨', '◎', '☁', '✦', '🐪');
