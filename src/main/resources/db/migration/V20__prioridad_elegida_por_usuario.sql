-- Prioridad elegida a mano. Cuando la persona fija la prioridad al crear el
-- ticket (o el personal la cambia), las automatizaciones (NEXA) ya no la
-- sobrescriben: antes un ticket marcado ALTA podía quedar en MEDIA, y con él
-- el plazo del SLA, sin que nadie se enterara.
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS priority_manual_ticket BOOLEAN NOT NULL DEFAULT FALSE;
