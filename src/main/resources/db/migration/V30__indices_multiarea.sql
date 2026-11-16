-- =============================================================================
-- Índices para la mesa multi-área.
--
-- Las consultas nuevas filtran por el área que atiende (cola del agente y del
-- jefe, dashboard del jefe), por el área del solicitante (seguimiento del jefe)
-- y por el área de cada persona (equipos con "todos los agentes", evaluaciones
-- por área). Con todas las áreas usando la mesa, la tabla de tickets crece
-- varias veces más rápido que cuando solo era de TI.
-- =============================================================================

-- Cola y dashboard por área que atiende, de lo más reciente a lo más antiguo.
-- Cubre también el índice simple de V28 (misma columna inicial).
CREATE INDEX IF NOT EXISTS idx_ticket_destino_recientes
    ON ticket (area_destino_ticket, created_at_ticket DESC) WHERE deleted_ticket = FALSE;
DROP INDEX IF EXISTS idx_ticket_area_destino;

-- Seguimiento del jefe: lo que pide la gente de su área.
CREATE INDEX IF NOT EXISTS idx_ticket_solicitante_area_recientes
    ON ticket (area_ticket, created_at_ticket DESC) WHERE deleted_ticket = FALSE;

-- Personas de un área (agentes del equipo, evaluaciones y jefaturas).
CREATE INDEX IF NOT EXISTS idx_users_area ON users (area_id) WHERE deleted_user = FALSE;

-- Miembros de un equipo y equipos de una persona (enrutamiento).
CREATE INDEX IF NOT EXISTS idx_team_user_team ON team_user (team_id) WHERE active_team_user = TRUE;
CREATE INDEX IF NOT EXISTS idx_team_user_user ON team_user (user_id);

-- Categorías vivas de un área (alta de ticket y clasificación de MAPI).
CREATE INDEX IF NOT EXISTS idx_category_area_viva ON category (area_category) WHERE deleted_category = FALSE;
