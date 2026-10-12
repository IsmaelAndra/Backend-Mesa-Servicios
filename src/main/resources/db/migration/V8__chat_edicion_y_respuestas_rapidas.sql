-- ============================================================================
-- CHAT: EDICIÓN DE MENSAJES Y RESPUESTAS RÁPIDAS
-- ============================================================================
-- edited_at_answer: cuándo se editó un mensaje por última vez (null = nunca).
-- El chat lo muestra como "editado"; el texto anterior queda en ticket_audit,
-- que es append-only, así que una edición nunca borra lo que se dijo.
--
-- quick_reply: textos guardados que el personal inserta en el chat con un clic
-- o escribiendo "/". Cada uno es de quien lo crea; los compartidos (los publica
-- un administrador) los ve todo el personal.
-- ============================================================================

ALTER TABLE answer ADD COLUMN IF NOT EXISTS edited_at_answer TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS quick_reply (
    id_quick_reply          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title_quick_reply       VARCHAR(80)  NOT NULL,
    content_quick_reply     TEXT         NOT NULL,
    shared_quick_reply      BOOLEAN      NOT NULL DEFAULT FALSE,
    owner_quick_reply       UUID         NOT NULL REFERENCES users (id_user),
    deleted_quick_reply     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at_quick_reply  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at_quick_reply  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_quick_reply_owner
    ON quick_reply (owner_quick_reply) WHERE deleted_quick_reply = FALSE;
