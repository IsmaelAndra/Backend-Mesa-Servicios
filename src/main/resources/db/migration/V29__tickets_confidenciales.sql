-- =============================================================================
-- Tickets confidenciales.
--
-- * ticket.confidencial_ticket: solo lo ven el solicitante, el agente asignado,
--   el jefe del área que lo atiende y las personas autorizadas.
-- * ticket.confidencial_area_ticket: además, todos los agentes del área.
-- * ticket_autorizado: personas añadidas a mano a un confidencial.
-- * acceso_confidencial: el súper admin o el auditor piden acceso con un motivo
--   y el jefe del área lo aprueba o rechaza. Aprobado vale unas horas, es de
--   solo lectura y cada apertura queda en la auditoría del ticket.
-- =============================================================================

ALTER TABLE ticket ADD COLUMN IF NOT EXISTS confidencial_ticket BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS confidencial_area_ticket BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_ticket_confidencial ON ticket (confidencial_ticket) WHERE confidencial_ticket = TRUE;

CREATE TABLE IF NOT EXISTS ticket_autorizado (
    id_ticket_autorizado          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_ticket_autorizado      UUID NOT NULL REFERENCES ticket (id_ticket),
    user_ticket_autorizado        UUID NOT NULL REFERENCES users (id_user),
    created_at_ticket_autorizado  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ticket_autorizado UNIQUE (ticket_ticket_autorizado, user_ticket_autorizado)
);
CREATE INDEX IF NOT EXISTS idx_ticket_autorizado_user ON ticket_autorizado (user_ticket_autorizado);

CREATE TABLE IF NOT EXISTS acceso_confidencial (
    id_acceso             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_acceso         UUID NOT NULL REFERENCES ticket (id_ticket),
    solicitante_acceso    UUID NOT NULL REFERENCES users (id_user),
    motivo_acceso         VARCHAR(500) NOT NULL,
    estado_acceso         VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    decidido_por_acceso   UUID REFERENCES users (id_user),
    comentario_acceso     VARCHAR(500),
    decidido_at_acceso    TIMESTAMPTZ,
    vence_at_acceso       TIMESTAMPTZ,
    created_at_acceso     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_estado_acceso CHECK (estado_acceso IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'))
);
CREATE INDEX IF NOT EXISTS idx_acceso_confidencial_ticket ON acceso_confidencial (ticket_acceso, solicitante_acceso);
CREATE INDEX IF NOT EXISTS idx_acceso_confidencial_pendiente ON acceso_confidencial (estado_acceso) WHERE estado_acceso = 'PENDIENTE';
