-- ============================================================
-- DATOS DE DESARROLLO - SOLO PERFIL "dev"
-- ============================================================
-- Este archivo se carga unicamente cuando SPRING_PROFILES_ACTIVE=dev
-- (ver spring.sql.init.data-locations en application-dev.yml).
-- En produccion NUNCA se ejecuta, y por eso aqui si se permite una contrasena
-- de ejemplo: son cuentas de prueba sin privilegios administrativos, en una
-- base de datos local.
--
-- Los usuarios administrativos (SUPER_ADMIN / ADMIN) no estan aqui: los crea
-- AdminSeedInitializer a partir de ADMIN_SEED_PASSWORD.

-- ===== USUARIOS DE PRUEBA =====
-- Contraseña: Default123! (hash BCrypt)
INSERT INTO users (identification_user, corporate_email_user, password_user, phone_user, status_user, rol_user, area_id, must_change_password) VALUES
    ('1000000003', 'agente@coacmagisterio.fin.ec',  '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000003', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'AGENT'), (SELECT id_area FROM area WHERE name_area = 'TI'), FALSE),
    ('1000000004', 'usuario@coacmagisterio.fin.ec', '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000004', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'USER'), (SELECT id_area FROM area WHERE name_area = 'Operaciones'), FALSE),
    -- Jefe de área de Operaciones: ve y gestiona los activos de su área.
    ('1000000005', 'jefe@coacmagisterio.fin.ec',    '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000005', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'AREA_MANAGER'), (SELECT id_area FROM area WHERE name_area = 'Operaciones'), FALSE)
ON CONFLICT (identification_user) DO NOTHING;

-- ===== RELACION USUARIOS-EQUIPOS =====
INSERT INTO team_user (team_id, user_id, active_team_user)
SELECT t.id_team, u.id_user, TRUE
FROM support_team t CROSS JOIN users u
WHERE t.name_team = 'Equipo TI'
  AND u.corporate_email_user IN ('agente@coacmagisterio.fin.ec')
ON CONFLICT (team_id, user_id) DO NOTHING;

INSERT INTO team_user (team_id, user_id, active_team_user)
SELECT t.id_team, u.id_user, TRUE
FROM support_team t CROSS JOIN users u
WHERE t.name_team = 'Equipo Operaciones'
  AND u.corporate_email_user IN ('usuario@coacmagisterio.fin.ec')
ON CONFLICT (team_id, user_id) DO NOTHING;

-- ===== ACTIVOS DE EJEMPLO =====
-- Tres activos que muestran las tres relaciones posibles: uno asignado a un
-- usuario, otro ligado a un área y otro a una sede. Con ON CONFLICT por código
-- para no duplicarlos si el proceso se re-ejecuta.
INSERT INTO asset (code_asset, name_asset, type_asset, brand_asset, model_asset, serial_number_asset,
                   status_asset, provider_asset, cost_asset, notes_asset,
                   assigned_user_id, area_id, sucursal_id) VALUES
    ('TI-LAP-001', 'Laptop Dell Latitude 5430', 'LAPTOP', 'Dell', 'Latitude 5430', '5CG-DEV-0001',
     'ASIGNADO', 'Tecnomega', 1250.00, 'Equipo asignado al agente de TI de ejemplo.',
     (SELECT id_user FROM users WHERE corporate_email_user = 'agente@coacmagisterio.fin.ec'),
     (SELECT id_area FROM area WHERE name_area = 'TI'),
     (SELECT id_sucursal FROM sucursal WHERE code_sucursal = 'MAT')),
    ('TI-MON-002', 'Monitor LG 27 pulgadas 4K', 'MONITOR', 'LG', '27UP850', 'LG-DEV-0002',
     'DISPONIBLE', 'Tecnomega', 420.00, 'Monitor vinculado al área de Operaciones.',
     NULL,
     (SELECT id_area FROM area WHERE name_area = 'Operaciones'),
     (SELECT id_sucursal FROM sucursal WHERE code_sucursal = 'QUI')),
    ('TI-IMP-003', 'Impresora HP LaserJet M404dn', 'IMPRESORA', 'HP', 'M404dn', 'HP-DEV-0003',
     'DISPONIBLE', 'Tecnomega', 310.00, 'Impresora ubicada en la sede Matriz.',
     NULL, NULL,
     (SELECT id_sucursal FROM sucursal WHERE code_sucursal = 'MAT'))
ON CONFLICT (code_asset) DO NOTHING;

-- Historial de ejemplo para el activo asignado (para que la ficha muestre trazos).
INSERT INTO asset_history (asset_id, user_id, action_type, notes)
SELECT a.id_asset, u.id_user, 'ASIGNACION', 'Asignación inicial (dato de ejemplo).'
FROM asset a CROSS JOIN users u
WHERE a.code_asset = 'TI-LAP-001'
  AND u.corporate_email_user = 'agente@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM asset_history h WHERE h.asset_id = a.id_asset);

-- Un equipo de Operaciones asignado a una persona del área: es lo que ve el
-- jefe de área (jefe@) además de los activos que son del área sin asignar.
INSERT INTO asset (code_asset, name_asset, type_asset, brand_asset, model_asset, serial_number_asset,
                   status_asset, provider_asset, cost_asset, notes_asset,
                   assigned_user_id, area_id, sucursal_id) VALUES
    ('OP-LAP-004', 'Laptop HP ProBook 450 G9', 'LAPTOP', 'HP', 'ProBook 450 G9', 'HP-DEV-0004',
     'ASIGNADO', 'Tecnomega', 980.00, 'Equipo de trabajo del área de Operaciones.',
     (SELECT id_user FROM users WHERE corporate_email_user = 'usuario@coacmagisterio.fin.ec'),
     (SELECT id_area FROM area WHERE name_area = 'Operaciones'),
     (SELECT id_sucursal FROM sucursal WHERE code_sucursal = 'QUI'))
ON CONFLICT (code_asset) DO NOTHING;

INSERT INTO asset_history (asset_id, user_id, action_type, notes)
SELECT a.id_asset, u.id_user, 'ASIGNACION', 'Asignación inicial (dato de ejemplo).'
FROM asset a CROSS JOIN users u
WHERE a.code_asset = 'OP-LAP-004'
  AND u.corporate_email_user = 'usuario@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM asset_history h WHERE h.asset_id = a.id_asset);
