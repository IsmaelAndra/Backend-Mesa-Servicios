-- ============================================================================
-- 1. MAPI responde primero
--    Al crearse un ticket, MAPI intenta resolverlo con una respuesta al
--    solicitante (pasos que puede hacer solo). Si le sirve, el solicitante lo da
--    por resuelto; si no, sigue con el agente.
-- ============================================================================
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS responde_primero BOOLEAN NOT NULL DEFAULT FALSE;

-- null = MAPI no propuso nada; PROPUESTA = esperando al solicitante;
-- RESUELTO = le sirvió; NECESITA_AGENTE = no le sirvió.
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS mapi_estado_ticket VARCHAR(20);

-- Mensaje escrito por MAPI (se pinta con su icono y no cuenta como la primera
-- respuesta del agente para el SLA).
ALTER TABLE answer ADD COLUMN IF NOT EXISTS mapi_answer BOOLEAN NOT NULL DEFAULT FALSE;

-- Usuario de sistema que firma los mensajes de MAPI. Inactivo y sin contraseña
-- válida: nadie puede iniciar sesión con él.
INSERT INTO users (id_user, identification_user, corporate_email_user, password_user, status_user,
                   rol_user, area_id, must_change_password, created_at_user, updated_at_user,
                   deleted_user, display_name, microsoft_synced)
SELECT '00000000-0000-0000-0000-0000000000a1', 'MAPI-SISTEMA', 'mapi@asistente.local', '!sin-acceso',
       'INACTIVE',
       (SELECT id_role FROM role WHERE name_role = 'USER' LIMIT 1),
       (SELECT id_area FROM area
         WHERE lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI'
         ORDER BY created_at_area LIMIT 1),
       FALSE, now(), now(), FALSE, 'MAPI', FALSE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id_user = '00000000-0000-0000-0000-0000000000a1')
  AND EXISTS (SELECT 1 FROM role WHERE name_role = 'USER')
  AND EXISTS (SELECT 1 FROM area WHERE lower(name_area) LIKE 'tecnolog%' OR upper(name_area) = 'TI');

-- ============================================================================
-- 2. Horario laboral y feriados para el SLA
--    Con horario laboral, el plazo de una política solo corre en horas hábiles:
--    un ticket de 4 h abierto el viernes a las 16:00 vence el lunes a las 11:00.
-- ============================================================================
CREATE TABLE IF NOT EXISTS horario_laboral (
    id_horario      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 1 = lunes … 7 = domingo (ISO)
    dia_horario     INTEGER NOT NULL UNIQUE CHECK (dia_horario BETWEEN 1 AND 7),
    inicio_horario  TIME NOT NULL DEFAULT '08:00',
    fin_horario     TIME NOT NULL DEFAULT '17:00',
    activo_horario  BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at_horario TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (fin_horario > inicio_horario)
);

INSERT INTO horario_laboral (dia_horario, inicio_horario, fin_horario, activo_horario)
SELECT d, '08:00', '17:00', d <= 5 FROM generate_series(1, 7) AS d
ON CONFLICT (dia_horario) DO NOTHING;

CREATE TABLE IF NOT EXISTS feriado (
    id_feriado          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha_feriado       DATE NOT NULL UNIQUE,
    nombre_feriado      VARCHAR(120) NOT NULL,
    -- Se repite cada año en el mismo día y mes (Navidad); si no, solo esa fecha
    -- (Carnaval, Viernes Santo y los traslados por decreto cambian cada año).
    anual_feriado       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at_feriado  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Feriados nacionales de Ecuador de fecha fija, más los móviles de 2027. El
-- administrador los corrige cuando el Gobierno los traslada.
INSERT INTO feriado (fecha_feriado, nombre_feriado, anual_feriado) VALUES
    ('2027-01-01', 'Año Nuevo', TRUE),
    ('2027-05-01', 'Día del Trabajo', TRUE),
    ('2027-05-24', 'Batalla de Pichincha', TRUE),
    ('2027-08-10', 'Primer Grito de Independencia', TRUE),
    ('2027-10-09', 'Independencia de Guayaquil', TRUE),
    ('2026-11-02', 'Día de Difuntos', TRUE),
    ('2026-11-03', 'Independencia de Cuenca', TRUE),
    ('2026-12-25', 'Navidad', TRUE),
    ('2027-02-08', 'Carnaval', FALSE),
    ('2027-02-09', 'Carnaval', FALSE),
    ('2027-03-26', 'Viernes Santo', FALSE)
ON CONFLICT (fecha_feriado) DO NOTHING;

-- Cada política decide si cuenta solo en horario laboral (lo habitual) o las
-- 24 horas (p. ej. una política crítica). Las que existen pasan a horario laboral.
ALTER TABLE sla_policy ADD COLUMN IF NOT EXISTS horario_laboral_sla_policy BOOLEAN NOT NULL DEFAULT TRUE;
