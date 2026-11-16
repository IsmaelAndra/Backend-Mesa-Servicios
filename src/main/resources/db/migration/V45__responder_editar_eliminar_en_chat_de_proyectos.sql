-- ============================================================================
-- CHAT DE PROYECTOS: RESPONDER, EDITAR Y ELIMINAR (COMO EN WHATSAPP)
-- ============================================================================
-- Responder cita otro mensaje del mismo proyecto; editar deja la marca
-- "editado"; un mensaje eliminado sigue en el hilo como "Se eliminó este
-- mensaje" (deleted_proyecto_mensaje ya existía desde V39).
-- ============================================================================

ALTER TABLE proyecto_mensaje ADD COLUMN IF NOT EXISTS editado_at_proyecto_mensaje TIMESTAMPTZ;
ALTER TABLE proyecto_mensaje ADD COLUMN IF NOT EXISTS responde_a_proyecto_mensaje UUID
    REFERENCES proyecto_mensaje (id_proyecto_mensaje);
