-- ============================================================================
-- HISTORIAL DE ACTIVOS: QUIÉN LO HIZO Y DE QUIÉN VENÍA
-- ============================================================================
-- En V11 cada fila tenía un solo usuario (user_id) y su significado cambiaba
-- según la acción: en una reasignación hecha desde "Asignar" era el usuario
-- ANTERIOR, en la hecha desde el formulario era el NUEVO, y en reparación o
-- baja era quien pulsaba el botón. El historial no permitía responder "¿quién
-- tenía el equipo?" ni "¿quién lo cambió?".
--
-- Desde aquí:
--   user_id           persona a la que afecta: el nuevo responsable en una
--                     asignación/reasignación, quien lo devuelve en una
--                     devolución, el responsable actual (si lo hay) en
--                     reparación o baja. Puede ser NULL (activo sin usuario).
--   previous_user_id  en una reasignación, quién lo tenía antes.
--   performed_by_id   quién hizo el cambio (administrador o jefe de área).
-- ============================================================================

ALTER TABLE asset_history ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE asset_history ADD COLUMN IF NOT EXISTS previous_user_id UUID;
ALTER TABLE asset_history ADD COLUMN IF NOT EXISTS performed_by_id UUID;

ALTER TABLE asset_history
    ADD CONSTRAINT fk_ah_previous_user FOREIGN KEY (previous_user_id) REFERENCES users (id_user) ON DELETE SET NULL;
ALTER TABLE asset_history
    ADD CONSTRAINT fk_ah_performed_by FOREIGN KEY (performed_by_id) REFERENCES users (id_user) ON DELETE SET NULL;

-- Borrar un usuario no debe borrar el rastro de los equipos que tuvo.
ALTER TABLE asset_history DROP CONSTRAINT IF EXISTS fk_ah_user;
ALTER TABLE asset_history
    ADD CONSTRAINT fk_ah_user FOREIGN KEY (user_id) REFERENCES users (id_user) ON DELETE SET NULL;

-- Alta del activo en el historial (antes solo quedaba en la auditoría).
ALTER TABLE asset_history DROP CONSTRAINT IF EXISTS chk_ah_action;
ALTER TABLE asset_history ADD CONSTRAINT chk_ah_action CHECK (action_type IN (
    'ALTA', 'ASIGNACION', 'REASIGNACION', 'DEVOLUCION', 'BAJA', 'REPARACION', 'FIN_REPARACION'
));

CREATE INDEX IF NOT EXISTS idx_asset_history_previous ON asset_history (previous_user_id);
CREATE INDEX IF NOT EXISTS idx_asset_history_performed ON asset_history (performed_by_id);
