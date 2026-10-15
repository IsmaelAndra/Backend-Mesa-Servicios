-- ============================================================================
-- Telefonía: llamadas entre extensiones de la central Grandstream UCM.
--
-- La UCM solo es accesible desde la red interna de la cooperativa, así que el
-- servidor no la llama directamente: deja la orden en la tabla "llamada" y un
-- puente instalado en un servidor interno (puente-telefonia/) la recoge por
-- HTTPS (conexión de salida, sin abrir puertos) y la ejecuta con la API de la
-- UCM (dialExtension).
-- ============================================================================

-- Extensión telefónica de cada persona (por ejemplo 182).
ALTER TABLE users ADD COLUMN IF NOT EXISTS extension_user VARCHAR(20);

CREATE TABLE IF NOT EXISTS llamada (
    id_llamada          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id           UUID REFERENCES ticket (id_ticket) ON DELETE SET NULL,
    caller_user_id      UUID REFERENCES users (id_user) ON DELETE SET NULL,
    callee_user_id      UUID REFERENCES users (id_user) ON DELETE SET NULL,
    caller_extension    VARCHAR(20)  NOT NULL,
    callee_extension    VARCHAR(20)  NOT NULL,
    status_llamada      VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
        CHECK (status_llamada IN ('PENDIENTE', 'ENVIADA', 'REALIZADA', 'ERROR', 'VENCIDA')),
    error_llamada       VARCHAR(500),
    created_at_llamada  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at_llamada  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_llamada_estado ON llamada (status_llamada, created_at_llamada);
CREATE INDEX IF NOT EXISTS idx_llamada_ticket ON llamada (ticket_id);
