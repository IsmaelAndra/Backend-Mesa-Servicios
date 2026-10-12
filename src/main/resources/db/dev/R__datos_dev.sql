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
     (SELECT id_role FROM role WHERE name_role = 'USER'), (SELECT id_area FROM area WHERE name_area = 'Operaciones'), FALSE)
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
