-- ============================================================================
-- COLA DE CORREO (OUTBOX)
-- ============================================================================
-- Todo correo se guarda primero aquí, en la misma transacción que el hecho que
-- lo provoca (el ticket creado, la respuesta...). Un proceso aparte lo envía por
-- Microsoft Graph al ritmo que permite el buzón (Exchange Online limita cada
-- buzón a unos 30 mensajes por minuto).
--
-- Antes se enviaba desde una cola EN MEMORIA de 500 huecos: en un pico se
-- llenaba y los correos se perdían, y un reinicio del backend tiraba lo que
-- estuviera esperando. Con la tabla, nada se pierde: como mucho, tarda más.
--
-- Estados:
--   PENDIENTE  esperando turno (o reintento, según next_attempt_at)
--   ENVIANDO   lo tiene un proceso; si muere, locked_until_mail lo libera
--   ENVIADO    aceptado por Graph
--   AGRUPADO   salió dentro de un correo resumen (varios avisos, un correo)
--   FALLIDO    agotó los reintentos; se puede reintentar a mano
--   CANCELADO  descartado a mano desde el panel
-- ============================================================================

CREATE TABLE IF NOT EXISTS mail_outbox (
    id_mail             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    to_mail             VARCHAR(320) NOT NULL,
    subject_mail        VARCHAR(500) NOT NULL,
    body_mail           TEXT         NOT NULL,
    type_mail           VARCHAR(60),
    priority_mail       SMALLINT     NOT NULL DEFAULT 5,
    status_mail         VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
        CHECK (status_mail IN ('PENDIENTE', 'ENVIANDO', 'ENVIADO', 'AGRUPADO', 'FALLIDO', 'CANCELADO')),
    attempts_mail       INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until_mail   TIMESTAMPTZ,
    last_error_mail     TEXT,
    notification_id     UUID,
    ticket_id           UUID,
    created_at_mail     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at_mail        TIMESTAMPTZ
);

-- La consulta del proceso de envío: los pendientes ya vencidos, por prioridad.
CREATE INDEX IF NOT EXISTS ix_mail_outbox_cola
    ON mail_outbox (priority_mail, created_at_mail) WHERE status_mail = 'PENDIENTE';
-- Panel: conteos y gráficas de las últimas horas.
CREATE INDEX IF NOT EXISTS ix_mail_outbox_creado ON mail_outbox (created_at_mail);
CREATE INDEX IF NOT EXISTS ix_mail_outbox_enviado ON mail_outbox (sent_at_mail) WHERE sent_at_mail IS NOT NULL;
