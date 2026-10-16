-- ============================================================================
-- Sonidos de las notificaciones, configurados por el SUPER_ADMIN para todos.
--
-- Antes la configuración vivía en el localStorage del navegador de quien la
-- hacía: solo le sonaba a él, y un MP3 de 1 MB llenaba el almacenamiento.
--
-- Dos ámbitos:
--   URGENCIA  sonido general de una urgencia (key: urgent, normal, soft).
--   TIPO      ajuste de un tipo de notificación (key: TICKET_ASIGNADO...):
--             otra urgencia y/o un audio propio, que manda sobre el general.
-- Silenciar un tipo sigue siendo una preferencia de cada persona (navegador).
-- ============================================================================

CREATE TABLE IF NOT EXISTS notification_sound (
    id_sound          UUID PRIMARY KEY,
    scope_sound       VARCHAR(10)  NOT NULL,
    key_sound         VARCHAR(60)  NOT NULL,
    urgency_sound     VARCHAR(10),
    audio_sound       BYTEA,
    mime_sound        VARCHAR(40),
    file_name_sound   VARCHAR(200),
    updated_by_sound  UUID,
    created_at_sound  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at_sound  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_sound_scope CHECK (scope_sound IN ('URGENCIA', 'TIPO')),
    CONSTRAINT ck_notification_sound_urgency CHECK (urgency_sound IS NULL OR urgency_sound IN ('urgent', 'normal', 'soft')),
    CONSTRAINT uq_notification_sound UNIQUE (scope_sound, key_sound)
);
