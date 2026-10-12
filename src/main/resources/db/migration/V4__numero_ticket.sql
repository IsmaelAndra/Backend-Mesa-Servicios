-- ============================================================================
-- NÚMERO DE TICKET LEGIBLE
-- ============================================================================
-- Desde V3__ids_uuid.sql la clave del ticket es un uuid: sirve para las URLs y
-- las relaciones, pero nadie dicta "3f2a9c1e-…" por teléfono. Las personas se
-- refieren al ticket por un número correlativo ("el ticket 43"), así que se
-- guarda aparte: number_ticket. Solo se usa para mostrar y para buscar; la
-- clave interna sigue siendo id_ticket.
--
-- Los tickets que ya existían reciben su número por orden de creación, de modo
-- que el más antiguo es el 1 y el orden se conserva.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS ticket_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE ticket ADD COLUMN IF NOT EXISTS number_ticket BIGINT;

UPDATE ticket t
SET number_ticket = o.n
FROM (
    SELECT id_ticket, row_number() OVER (ORDER BY created_at_ticket, id_ticket) AS n
    FROM ticket
) o
WHERE t.id_ticket = o.id_ticket
  AND t.number_ticket IS NULL;

-- La secuencia sigue desde el último número asignado.
SELECT setval('ticket_number_seq', COALESCE((SELECT max(number_ticket) FROM ticket), 0) + 1, false);

ALTER TABLE ticket ALTER COLUMN number_ticket SET DEFAULT nextval('ticket_number_seq');
ALTER TABLE ticket ALTER COLUMN number_ticket SET NOT NULL;
ALTER SEQUENCE ticket_number_seq OWNED BY ticket.number_ticket;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ticket_number ON ticket (number_ticket);
