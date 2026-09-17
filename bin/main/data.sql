-- ============================================================
-- DATOS INICIALES - SISTEMA MESA DE AYUDA COAC
-- Idempotente: INSERT ... ON CONFLICT DO NOTHING
-- Dominio corporativo: @coacmagisterio.fin.ec
-- Contraseña inicial para todos: Default123! (deben cambiarla al primer login)
-- ============================================================

-- ===== ROLES =====
INSERT INTO role (name_role, description_role) VALUES
    ('SUPER_ADMIN', 'Super administrador con acceso total al sistema, configuración de marca, SLA, equipos, maestros'),
    ('ADMIN',       'Administrador del sistema con permisos de gestión de usuarios, tickets, reportes'),
    ('AGENT',       'Agente de soporte de mesa de ayuda, resuelve tickets asignados'),
    ('USER',        'Usuario final solicitante de tickets')
ON CONFLICT (name_role) DO NOTHING;

-- ===== ÁREAS =====
INSERT INTO area (name_area, description_area, priority_level, status_area) VALUES
    ('TI',          'Tecnología de la información', 5, TRUE),
    ('Operaciones', 'Área operativa principal', 3, TRUE),
    ('Finanzas',    'Gestión financiera y contable', 4, TRUE),
    ('RRHH',        'Recursos humanos', 2, TRUE),
    ('Atención',    'Atención y servicio al cliente', 3, TRUE)
ON CONFLICT (name_area) DO NOTHING;

-- ===== ESTADOS DE TICKET =====
INSERT INTO status (name_status, description_status, icon_status, color_status) VALUES
    ('NUEVO',      'Ticket recién creado, sin asignar', 'pi pi-inbox',            '#3B82F6'),
    ('ASIGNADO',   'Ticket asignado a un agente',      'pi pi-user',              '#8B5CF6'),
    ('EN_PROCESO', 'Agente trabajando en el ticket',   'pi pi-spinner',           '#F59E0B'),
    ('RESUELTO',   'Problema resuelto, pendiente confirmación', 'pi pi-check-circle','#10B981'),
    ('CERRADO',    'Ticket cerrado y confirmado',      'pi pi-lock',              '#6B7280'),
    ('REABIERTO',  'Ticket reabierto por el usuario',  'pi pi-refresh',           '#EF4444'),
    ('ESCALADO',   'Ticket escalado a otro equipo',    'pi pi-arrow-up-right',    '#EC4899')
ON CONFLICT (name_status) DO NOTHING;

-- ===== PRIORIDADES =====
INSERT INTO priority (name_priority, level_priority, description_priority, icon_priority, color_priority) VALUES
    ('BAJA',   1, 'Sin impacto en operación',  'pi pi-arrow-down',  '#10B981'),
    ('MEDIA',  2, 'Impacto parcial',           'pi pi-minus',       '#3B82F6'),
    ('ALTA',   3, 'Impacto significativo',     'pi pi-arrow-up',    '#F59E0B'),
    ('CRITICA',4, 'Servicio detenido',         'pi pi-exclamation-triangle', '#EF4444')
ON CONFLICT (name_priority) DO NOTHING;

-- ===== EQUIPOS DE SOPORTE =====
INSERT INTO support_team (name_team, description_team, assignment_method, active_team) VALUES
    ('Equipo TI',       'Soporte de infraestructura y sistemas', 'ROUND_ROBIN', TRUE),
    ('Equipo Operaciones', 'Soporte de procesos operativos',     'LEAST_ASSIGNED', TRUE),
    ('Equipo RRHH',     'Soporte de gestión humana',             'MANUAL', TRUE),
    ('Equipo Finanzas', 'Soporte contable y financiero',          'ROUND_ROBIN', TRUE)
ON CONFLICT (name_team) DO NOTHING;

-- ===== TIPOS DE TICKET =====
INSERT INTO ticket_type (name_ticket_type, description_ticket_type) VALUES
    ('INCIDENTE',    'Falla o interrupción de un servicio existente'),
    ('REQUERIMIENTO','Solicitud formal de un bien, servicio o cambio'),
    ('CONSULTA',     'Pregunta o aclaración sobre un tema'),
    ('PROYECTO',     'Trabajo planificado con alcance y fechas definidas')
ON CONFLICT (name_ticket_type) DO NOTHING;

-- Asignar un tipo a los tickets demo existentes
UPDATE ticket SET type_ticket = (SELECT id_ticket_type FROM ticket_type WHERE name_ticket_type = 'INCIDENTE')
WHERE title_ticket = 'Falla de internet en bodega central';
UPDATE ticket SET type_ticket = (SELECT id_ticket_type FROM ticket_type WHERE name_ticket_type = 'CONSULTA')
WHERE title_ticket = 'Consulta de facturación de proveedores';
UPDATE ticket SET type_ticket = (SELECT id_ticket_type FROM ticket_type WHERE name_ticket_type = 'REQUERIMIENTO')
WHERE title_ticket = 'Actualización de datos personales en nómina';
UPDATE ticket SET type_ticket = (SELECT id_ticket_type FROM ticket_type WHERE name_ticket_type = 'REQUERIMIENTO')
WHERE title_ticket = 'Proceso de contratación de pasante';

-- ===== CATEGORÍAS =====
INSERT INTO category (name_category, description_category, icon_category, color_category, team_category) VALUES
    ('Soporte Técnico',   'Problemas de hardware y software',           'pi pi-desktop',    '#3B82F6', (SELECT id_team FROM support_team WHERE name_team = 'Equipo TI')),
    ('Redes',             'Conectividad y red',                          'pi pi-sitemap',    '#8B5CF6', (SELECT id_team FROM support_team WHERE name_team = 'Equipo TI')),
    ('Correo',            'Problemas con correo corporativo',            'pi pi-envelope',   '#F59E0B', (SELECT id_team FROM support_team WHERE name_team = 'Equipo TI')),
    ('Ofimática',         'Microsoft Office y herramientas ofimáticas',  'pi pi-file-excel', '#10B981', (SELECT id_team FROM support_team WHERE name_team = 'Equipo TI')),
    ('Procesos',          'Soporte de procesos operativos',              'pi pi-cog',       '#EF4444', (SELECT id_team FROM support_team WHERE name_team = 'Equipo Operaciones')),
    ('Contratación',      'Soporte de contrataciones y nómina',          'pi pi-users',     '#EC4899', (SELECT id_team FROM support_team WHERE name_team = 'Equipo RRHH')),
    ('Facturación',       'Consultas de facturación y pagos',            'pi pi-dollar',    '#F59E0B', (SELECT id_team FROM support_team WHERE name_team = 'Equipo Finanzas'))
ON CONFLICT (name_category) DO NOTHING;

-- ===== POLÍTICAS SLA =====
INSERT INTO sla_policy (name_sla_policy, description_sla_policy, response_time_minutes, resolution_time_minutes) VALUES
    ('SLA Crítico',  'Respuesta inmediata, resolución el mismo día',   30,  480),
    ('SLA Alto',     'Respuesta en 1 hora, resolución en 1 día',       60,  1440),
    ('SLA Medio',    'Respuesta en 4 horas, resolución en 2 días',     240, 2880),
    ('SLA Bajo',     'Respuesta en 8 horas, resolución en 4 días',     480, 5760),
    ('SLA Interno',  'Soporte interno estándar',                       120, 2400)
ON CONFLICT (name_sla_policy) DO NOTHING;

-- ===== REGLAS SLA (área + prioridad + categoría) =====
-- Una regla por combinación (área × prioridad × categoría) para que todo ticket tenga SLA.
INSERT INTO sla_rule (sla_policy_id, area_id, priority_id, category_id, active_sla_rule)
SELECT slp.id_sla_policy, ar.id_area, pr.id_priority, ct.id_category, TRUE
FROM sla_policy slp
CROSS JOIN area ar
CROSS JOIN priority pr
CROSS JOIN category ct
WHERE ct.deleted_category = FALSE
  AND slp.name_sla_policy = CASE
        WHEN pr.name_priority = 'CRITICA' THEN 'SLA Crítico'
        WHEN pr.name_priority = 'ALTA'    THEN 'SLA Alto'
        WHEN pr.name_priority = 'MEDIA'   THEN 'SLA Medio'
        ELSE 'SLA Bajo'
    END
ON CONFLICT DO NOTHING;

-- ===== USUARIOS INICIALES (4 usuarios de prueba) =====
-- Contraseña: Default123! (hash BCrypt: $2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a)
-- Dominio: @coacmagisterio.fin.ec
-- NOTA: must_change_password=TRUE - usuarios deben cambiarla al primer login
INSERT INTO users (identification_user, corporate_email_user, password_user, phone_user, status_user, rol_user, area_id, must_change_password) VALUES
    ('1000000001', 'superadmin@coacmagisterio.fin.ec', '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000001', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'SUPER_ADMIN'), (SELECT id_area FROM area WHERE name_area = 'TI'), TRUE),
    ('1000000002', 'admin@coacmagisterio.fin.ec',       '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000002', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'ADMIN'), (SELECT id_area FROM area WHERE name_area = 'TI'), TRUE),
    ('1000000003', 'agente@coacmagisterio.fin.ec',      '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000003', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'AGENT'), (SELECT id_area FROM area WHERE name_area = 'TI'), TRUE),
    ('1000000004', 'usuario@coacmagisterio.fin.ec',     '$2a$10$4ZvAPuWCFLdtipaZ1gSdVOFxC28PmiUN01I6ICHXRizmgpYDWIB5a', '0990000004', 'ACTIVE',
     (SELECT id_role FROM role WHERE name_role = 'USER'), (SELECT id_area FROM area WHERE name_area = 'Operaciones'), TRUE)
ON CONFLICT (identification_user) DO NOTHING;

-- ===== RELACION USUARIOS-EQUIPOS =====
INSERT INTO team_user (team_id, user_id, active_team_user)
SELECT t.id_team, u.id_user, TRUE
FROM support_team t CROSS JOIN users u
WHERE t.name_team = 'Equipo TI' 
  AND u.corporate_email_user IN ('superadmin@coacmagisterio.fin.ec','admin@coacmagisterio.fin.ec','agente@coacmagisterio.fin.ec')
ON CONFLICT (team_id, user_id) DO NOTHING;

INSERT INTO team_user (team_id, user_id, active_team_user)
SELECT t.id_team, u.id_user, TRUE
FROM support_team t CROSS JOIN users u
WHERE t.name_team = 'Equipo Operaciones' 
  AND u.corporate_email_user = 'usuario@coacmagisterio.fin.ec'
ON CONFLICT (team_id, user_id) DO NOTHING;

-- ===== ESCALAMIENTOS SLA =====
INSERT INTO sla_escalation (sla_policy_id, trigger_percentage, action_type, destination_team_id, destination_user_id, notification_template_id) VALUES
    ((SELECT id_sla_policy FROM sla_policy WHERE name_sla_policy = 'SLA Crítico'), 80, 'NOTIFY_SUPERVISOR',
     (SELECT id_team FROM support_team WHERE name_team = 'Equipo TI'), NULL, NULL),
    ((SELECT id_sla_policy FROM sla_policy WHERE name_sla_policy = 'SLA Crítico'), 100, 'ESCALATE',
     (SELECT id_team FROM support_team WHERE name_team = 'Equipo TI'), NULL, NULL)
ON CONFLICT DO NOTHING;

-- ===== PLANTILLAS DE NOTIFICACIÓN =====
INSERT INTO notification_template (name_template, type_template, subject_template, body_template, active_template) VALUES
    ('TICKET_CREADO',    'TICKET', 'Ticket #[id] creado',                       'Estimado/a [nombre], su ticket ha sido creado correctamente.', TRUE),
    ('TICKET_ASIGNADO',  'TICKET', 'Ticket #[id] asignado',                     'Ticket asignado al agente [agente].', TRUE),
    ('TICKET_ACTUALIZADO','TICKET','Ticket #[id] actualizado',                  'Su ticket ha sido actualizado.', TRUE),
    ('TICKET_RESUELTO',  'TICKET', 'Ticket #[id] resuelto',                     'Su ticket ha sido resuelto.', TRUE),
    ('TICKET_CERRADO',   'TICKET', 'Ticket #[id] cerrado',                      'Su ticket ha sido cerrado.', TRUE),
    ('RATING_REQUEST',   'RATING', 'Califique su ticket #[id]',                 'Le invitamos a calificar la atención recibida.', TRUE),
    ('SLA_WARNING',      'SLA',    'SLA por vencer - Ticket #[id]',             'El SLA del ticket está al 80%.', TRUE),
    ('SLA_BREACHED',     'SLA',    'SLA vencido - Ticket #[id]',                'El SLA del ticket se ha vencido.', TRUE),
    ('ESCALATION',       'SLA',    'Ticket #[id] escalado',                     'El ticket ha sido escalado.', TRUE),
    ('MONTHLY_EVALUATION','EVAL',  'Evaluación mensual disponible',             'Su evaluación mensual está disponible.', TRUE),
    ('TASK_ASIGNADA',    'TASK',   'Tarea asignada',                            'Se le ha asignado una tarea. Revise el detalle de fecha y hora acordada.', TRUE)
ON CONFLICT (name_template) DO NOTHING;

-- ===== CONFIGURACIÓN DE MARCA (BRANDING) =====
INSERT INTO branding_config (logo_url, background_url, primary_color, secondary_color)
SELECT NULL, '/uploads/fondo.jfif', '#062e86', '#ec9811'
WHERE NOT EXISTS (SELECT 1 FROM branding_config);

-- Usa fondo por defecto si aún no tiene imagen de fondo configurada
UPDATE branding_config
SET background_url = '/uploads/fondo.jfif'
WHERE (background_url IS NULL OR background_url = '');
-- ============================================================
-- DATOS DEMO (solo si aún no existen)
-- ============================================================
-- Datos demo idempotentes: solo inserta si no existen aún

-- ===== TICKETS DEMO ADICIONALES =====
INSERT INTO ticket (title_ticket, description_ticket, user_ticket, agent_ticket, team_ticket, area_ticket, category_ticket, status_ticket, priority_ticket, closing_date_ticket, created_at_ticket, updated_at_ticket)
SELECT 'Falla de internet en bodega central', 'Desde esta mañana no hay conectividad en la bodega central, se afecta el despacho.', u.id_user, a.id_user, t.id_team, ar.id_area, c.id_category, st.id_status, p.id_priority, NULL, TIMESTAMPTZ '2026-09-04 08:10:00-05:00', NOW()
FROM users u, users a, area ar, category c, status st, priority p, support_team t
WHERE u.corporate_email_user='usuario@coacmagisterio.fin.ec' AND a.corporate_email_user='admin@coacmagisterio.fin.ec'
  AND ar.name_area='Operaciones' AND c.name_category='Redes' AND st.name_status='EN_PROCESO' AND p.name_priority='CRITICA'
  AND t.name_team='Equipo TI' AND NOT EXISTS (SELECT 1 FROM ticket x WHERE x.title_ticket='Falla de internet en bodega central');

INSERT INTO ticket (title_ticket, description_ticket, user_ticket, agent_ticket, team_ticket, area_ticket, category_ticket, status_ticket, priority_ticket, closing_date_ticket, created_at_ticket, updated_at_ticket)
SELECT 'Consulta de facturación de proveedores', 'Solicito el detalle de pagos del proveedor AUTOMOTRIZ ANDINA del mes de agosto.', u.id_user, a.id_user, t.id_team, ar.id_area, c.id_category, st.id_status, p.id_priority, TIMESTAMPTZ '2026-09-08 16:00:00-05:00', TIMESTAMPTZ '2026-09-07 09:30:00-05:00', NOW()
FROM users u, users a, area ar, category c, status st, priority p, support_team t
WHERE u.corporate_email_user='usuario@coacmagisterio.fin.ec' AND a.corporate_email_user='agente@coacmagisterio.fin.ec'
  AND ar.name_area='Finanzas' AND c.name_category='Facturación' AND st.name_status='RESUELTO' AND p.name_priority='MEDIA'
  AND t.name_team='Equipo Finanzas' AND NOT EXISTS (SELECT 1 FROM ticket x WHERE x.title_ticket='Consulta de facturación de proveedores');

INSERT INTO ticket (title_ticket, description_ticket, user_ticket, agent_ticket, team_ticket, area_ticket, category_ticket, status_ticket, priority_ticket, closing_date_ticket, created_at_ticket, updated_at_ticket)
SELECT 'Actualización de datos personales en nómina', 'Necesito actualizar mi fecha de nacimiento en el sistema de nómina.', u.id_user, a.id_user, t.id_team, ar.id_area, c.id_category, st.id_status, p.id_priority, NULL, TIMESTAMPTZ '2026-09-09 11:20:00-05:00', NOW()
FROM users u, users a, area ar, category c, status st, priority p, support_team t
WHERE u.corporate_email_user='usuario@coacmagisterio.fin.ec' AND a.corporate_email_user='agente@coacmagisterio.fin.ec'
  AND ar.name_area='RRHH' AND c.name_category='Contratación' AND st.name_status='ASIGNADO' AND p.name_priority='BAJA'
  AND t.name_team='Equipo RRHH' AND NOT EXISTS (SELECT 1 FROM ticket x WHERE x.title_ticket='Actualización de datos personales en nómina');

INSERT INTO ticket (title_ticket, description_ticket, user_ticket, agent_ticket, team_ticket, area_ticket, category_ticket, status_ticket, priority_ticket, closing_date_ticket, created_at_ticket, updated_at_ticket)
SELECT 'Proceso de contratación de pasante', 'El proceso de selección de pasante requiere apoyo del área de procesos para la etapa de inducción.', u.id_user, a.id_user, t.id_team, ar.id_area, c.id_category, st.id_status, p.id_priority, NULL, TIMESTAMPTZ '2026-09-03 14:45:00-05:00', NOW()
FROM users u, users a, area ar, category c, status st, priority p, support_team t
WHERE u.corporate_email_user='usuario@coacmagisterio.fin.ec' AND a.corporate_email_user='admin@coacmagisterio.fin.ec'
  AND ar.name_area='Operaciones' AND c.name_category='Procesos' AND st.name_status='ESCALADO' AND p.name_priority='ALTA'
  AND t.name_team='Equipo Operaciones' AND NOT EXISTS (SELECT 1 FROM ticket x WHERE x.title_ticket='Proceso de contratación de pasante');

-- ===== SLA DE LOS TICKETS DEMO =====
INSERT INTO ticket_sla (ticket_id, sla_policy_id, response_due_at, resolution_due_at, response_completed_at, resolution_completed_at, response_breached, resolution_breached)
SELECT tk.id_ticket, p.id_sla_policy, tk.created_at_ticket + (p.response_time_minutes || ' minutes')::interval, tk.created_at_ticket + (p.resolution_time_minutes || ' minutes')::interval,
       CASE WHEN st.name_status IN ('RESUELTO','CERRADO') THEN tk.created_at_ticket + (p.response_time_minutes || ' minutes')::interval ELSE NULL END,
       CASE WHEN st.name_status IN ('RESUELTO','CERRADO') THEN tk.updated_at_ticket ELSE NULL END,
       FALSE, FALSE
FROM ticket tk JOIN status st ON st.id_status = tk.status_ticket JOIN priority pr ON pr.id_priority = tk.priority_ticket JOIN sla_policy p
  ON p.name_sla_policy = CASE WHEN pr.name_priority='CRITICA' THEN 'SLA Crítico' WHEN pr.name_priority='ALTA' THEN 'SLA Alto' WHEN pr.name_priority='MEDIA' THEN 'SLA Medio' ELSE 'SLA Bajo' END
WHERE tk.title_ticket IN ('Falla de internet en bodega central','Consulta de facturación de proveedores','Actualización de datos personales en nómina','Proceso de contratación de pasante')
  AND NOT EXISTS (SELECT 1 FROM ticket_sla ts WHERE ts.ticket_id = tk.id_ticket);

-- ===== RESPUESTAS DEMO =====
INSERT INTO answer (message_answer, internal_answer, ticket_answer, user_answer)
SELECT 'Estimado/a, le confirmamos que el módulo de consulta ya fue actualizado y el detalle solicitado está disponible en el portal de proveedores.', FALSE, tk.id_ticket, us.id_user
FROM ticket tk, users us
WHERE tk.title_ticket='Consulta de facturación de proveedores' AND us.corporate_email_user='agente@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM answer a WHERE a.ticket_answer=tk.id_ticket AND a.message_answer LIKE '%módulo de consulta ya fue actualizado%');

INSERT INTO answer (message_answer, internal_answer, ticket_answer, user_answer)
SELECT 'Se derivó al responsable de contrataciones para coordinar la inducción del pasante.', TRUE, tk.id_ticket, us.id_user
FROM ticket tk, users us
WHERE tk.title_ticket='Proceso de contratación de pasante' AND us.corporate_email_user='admin@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM answer a WHERE a.ticket_answer=tk.id_ticket AND a.message_answer LIKE '%coordinarse de la inducción%');

INSERT INTO answer (message_answer, internal_answer, ticket_answer, user_answer)
SELECT 'Revisando el estado de la red en bodega central, se detectó conmutador averiado. Gestionando reemplazo.', FALSE, tk.id_ticket, us.id_user
FROM ticket tk, users us
WHERE tk.title_ticket='Falla de internet en bodega central' AND us.corporate_email_user='admin@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM answer a WHERE a.ticket_answer=tk.id_ticket AND a.message_answer LIKE '%conmutador averiado%');

-- ===== CALIFICACIONES DEMO =====
INSERT INTO ticket_rating (ticket_id, user_id, requirements_met, all_requirements_met, response_time_rating, resolution_quality, agent_communication, overall_satisfaction, comments, rated_at, evaluation_month)
SELECT tk.id_ticket, u.id_user, 5, TRUE, 5, 5, 5, 5, 'Excelente atención, muy rápida.', NOW(), DATE '2026-09-01'
FROM ticket tk, users u
WHERE tk.title_ticket='Consulta de facturación de proveedores' AND u.corporate_email_user='usuario@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM ticket_rating r WHERE r.ticket_id = tk.id_ticket);

-- ===== EVALUACIONES MENSUALES DEMO =====
INSERT INTO monthly_evaluation (agent_id, evaluation_month, tickets_closed, avg_response_time_minutes, avg_resolution_time_minutes, sla_compliance_rate, avg_requirements_met, avg_response_time_rating, avg_resolution_quality, avg_agent_communication, avg_overall_satisfaction, requirements_met_count, satisfaction_score, supervisor_rating, supervisor_comments, evaluated_by, status)
SELECT u.id_user, m.month, m.closed, m.resp, m.resol, m.sla, m.prom, m.r_t, m.r_q, m.r_c, m.r_s, m.req, m.sat, m.sup, m.com, ev.id_user, m.st
FROM users u, users ev,
(VALUES
 ('agente@coacmagisterio.fin.ec', DATE '2026-08-01', 14, 12.5, 480.2, 95.50, 4.80, 4.80, 4.90, 4.85, 4.75, 5, 4.70, 5, 'Excelente mes, muy buen desempeño y cumplimiento de SLA.', 'REVIEWED'),
 ('agente@coacmagisterio.fin.ec', DATE '2026-09-01', 6,  15.0, 500.0, 90.00, 4.60, 4.60, 4.70, 4.70, 4.40, 4, 4.40, 4, 'Buen desempeño, mantener comunicación con usuarios.', 'COMPLETED'),
 ('nuevoagente@coacmagisterio.fin.ec', DATE '2026-08-01', 9, 20.0, 700.4, 88.20, 4.50, 4.50, 4.40, 4.60, 4.20, 4, 4.20, 4, 'Adaptación correcta a su primer mes.', 'REVIEWED'),
 ('nuevoagente@coacmagisterio.fin.ec', DATE '2026-09-01', 3, 10.0, 300.0, 100.00, 5.00, 5.00, 5.00, 5.00, 4.80, 5, 4.80, 5, 'Excelente arranque, cero incumplimientos de SLA.', 'COMPLETED')
) AS m(email, month, closed, resp, resol, sla, prom, r_t, r_q, r_c, r_s, req, sat, sup, com, st)
WHERE u.corporate_email_user = m.email AND ev.corporate_email_user = 'admin@coacmagisterio.fin.ec'
  AND NOT EXISTS (SELECT 1 FROM monthly_evaluation me WHERE me.agent_id = u.id_user AND me.evaluation_month = m.month);

