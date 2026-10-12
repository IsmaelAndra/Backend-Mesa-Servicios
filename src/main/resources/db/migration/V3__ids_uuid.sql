-- ============================================================================
-- IDs UUID: todas las claves primarias pasan de BIGINT GENERATED a uuid.
-- ============================================================================
-- Las tablas legacy se crearon con SERIAL/IDENTITY y el sistema ya podia tener
-- datos. En lugar de borrar la base, se convierte cada tabla IN SITU: cada fila
-- recibe un uuid nuevo y las columnas que la referencian se reescriben con el
-- mismo valor, de modo que la integridad se conserva sin tocar V1.
--
-- Como se hace:
--   1. Se retiran las claves foraneas (un alter de columna no puede convivir
--      con una FK que la apunte) y los triggers de solo insercion de auditoria
--      (bloquearian los UPDATE del remapeo).
--   2. Por cada tabla se construye un mapa viejo->nuevo en una tabla temporal.
--   3. La PK se convierte en tres pasos: ADD columna uuid, UPDATE con el mapa,
--      DROP de la columna legacy y RENAME. Con "GENERATED ALWAYS AS IDENTITY"
--      no se puede cambiar el tipo en el lugar, asi que el cambio en dos
--      columnas es la vía limpia.
--   4. Las columnas de referencia de los hijos se convierten con el mapa de su
--      padre (ADD + UPDATE + DROP + RENAME).
--   5. Al final se repone todo: FKs, constraints UNIQUE, indices, las
--      restricciones de fila unica de las tablas singleton y los triggers.
--
-- Las seis tablas singleton (routing_config, session_config, chat_config,
-- error_page_config, privacy_config y mfa_setting) conservan un uuid FIJO,
-- el mismo que usan las entidades Java (ver IdsConfiguracion), para que el
-- panel siga encontrandolas sin depender del orden de insercion.
--
-- El lector de scripts de Spring parte por ';' respetando comillas simples,
-- asi que aqui no se usa dollar-quoting (ver SqlScriptsValidationTest).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0) TRIGGERS DE SOLO INSERCION
-- ----------------------------------------------------------------------------
-- Los triggers de audit_log y ticket_audit impiden cualquier UPDATE, y el
-- remapeo de abajo necesita reescribir sus columnas. Se retiran al principio y
-- se vuelven a crear al final (funcion auditoria_solo_insercion intacta).
DROP TRIGGER IF EXISTS trg_audit_log_inmutable ON audit_log;
DROP TRIGGER IF EXISTS trg_audit_log_sin_truncate ON audit_log;
DROP TRIGGER IF EXISTS trg_ticket_audit_inmutable ON ticket_audit;
DROP TRIGGER IF EXISTS trg_ticket_audit_sin_truncate ON ticket_audit;

-- ----------------------------------------------------------------------------
-- 1) SE RETIRAN LAS CLAVES FORANEAS
-- ----------------------------------------------------------------------------
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_role;
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_area;
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_sucursal;
ALTER TABLE team_user DROP CONSTRAINT IF EXISTS fk_tu_team;
ALTER TABLE team_user DROP CONSTRAINT IF EXISTS fk_tu_user;
ALTER TABLE category DROP CONSTRAINT IF EXISTS fk_category_team;
ALTER TABLE sla_rule DROP CONSTRAINT IF EXISTS fk_slarule_policy;
ALTER TABLE sla_rule DROP CONSTRAINT IF EXISTS fk_slarule_area;
ALTER TABLE sla_rule DROP CONSTRAINT IF EXISTS fk_slarule_priority;
ALTER TABLE sla_rule DROP CONSTRAINT IF EXISTS fk_slarule_category;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_user;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_agent;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_team;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_area;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_category;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_status;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_priority;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_sucursal;
ALTER TABLE ticket_sla DROP CONSTRAINT IF EXISTS fk_ts_ticket;
ALTER TABLE ticket_sla DROP CONSTRAINT IF EXISTS fk_ts_policy;
ALTER TABLE answer DROP CONSTRAINT IF EXISTS fk_answer_ticket;
ALTER TABLE answer DROP CONSTRAINT IF EXISTS fk_answer_user;
ALTER TABLE attach DROP CONSTRAINT IF EXISTS fk_attach_ticket;
ALTER TABLE attach DROP CONSTRAINT IF EXISTS fk_attach_answer;
ALTER TABLE ticket_audit DROP CONSTRAINT IF EXISTS fk_audit_ticket;
ALTER TABLE ticket_audit DROP CONSTRAINT IF EXISTS fk_audit_user;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS fk_notif_ticket;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS fk_notif_user;
ALTER TABLE sla_escalation DROP CONSTRAINT IF EXISTS fk_esc_policy;
ALTER TABLE sla_escalation DROP CONSTRAINT IF EXISTS fk_esc_team;
ALTER TABLE sla_escalation DROP CONSTRAINT IF EXISTS fk_esc_user;
ALTER TABLE sla_escalation DROP CONSTRAINT IF EXISTS fk_esc_template;
ALTER TABLE ticket_rating DROP CONSTRAINT IF EXISTS fk_rating_ticket;
ALTER TABLE ticket_rating DROP CONSTRAINT IF EXISTS fk_rating_user;
ALTER TABLE monthly_evaluation DROP CONSTRAINT IF EXISTS fk_eval_agent;
ALTER TABLE monthly_evaluation DROP CONSTRAINT IF EXISTS fk_eval_by;
ALTER TABLE task DROP CONSTRAINT IF EXISTS fk_task_assigned;
ALTER TABLE task DROP CONSTRAINT IF EXISTS fk_task_created;
ALTER TABLE answer_read DROP CONSTRAINT IF EXISTS fk_answer_read_answer;
ALTER TABLE answer_read DROP CONSTRAINT IF EXISTS fk_answer_read_user;
ALTER TABLE refresh_token DROP CONSTRAINT IF EXISTS fk_refresh_user;
ALTER TABLE notification_template_image DROP CONSTRAINT IF EXISTS fk_template_image_template;
ALTER TABLE password_reset_token DROP CONSTRAINT IF EXISTS fk_pw_reset_user;
ALTER TABLE user_mfa DROP CONSTRAINT IF EXISTS fk_user_mfa_user;
ALTER TABLE mfa_challenge DROP CONSTRAINT IF EXISTS fk_mfa_challenge_user;
ALTER TABLE mfa_recovery_code DROP CONSTRAINT IF EXISTS fk_mfa_recovery_user;
ALTER TABLE workflow DROP CONSTRAINT IF EXISTS fk_workflow_creado_por;
ALTER TABLE workflow_version DROP CONSTRAINT IF EXISTS fk_wf_version_wf;
ALTER TABLE workflow_version DROP CONSTRAINT IF EXISTS fk_wf_version_creado_por;
ALTER TABLE workflow_execution DROP CONSTRAINT IF EXISTS fk_wf_exec_wf;
ALTER TABLE workflow_execution DROP CONSTRAINT IF EXISTS fk_wf_exec_version;
ALTER TABLE workflow_execution DROP CONSTRAINT IF EXISTS fk_wf_exec_iniciado_por;
ALTER TABLE workflow_node_run DROP CONSTRAINT IF EXISTS fk_node_run_exec;
ALTER TABLE workflow_wait DROP CONSTRAINT IF EXISTS fk_workflow_wait_exec;
ALTER TABLE workflow_wait DROP CONSTRAINT IF EXISTS fk_workflow_wait_approver_user;
ALTER TABLE workflow_wait DROP CONSTRAINT IF EXISTS fk_workflow_wait_decided_by;
ALTER TABLE workflow_event_dedup DROP CONSTRAINT IF EXISTS fk_event_dedup_wf;
ALTER TABLE workflow_event_dedup DROP CONSTRAINT IF EXISTS fk_event_dedup_exec;

-- ----------------------------------------------------------------------------
-- 2) MAPA VIEJO -> NUEVO POR TABLA
-- ----------------------------------------------------------------------------
-- Una tabla temporal por entidad: la fila debe convertirse con el MISMO uuid
-- que la fila de la tabla padre, y los hijos la volveran a usar via JOIN.
CREATE TEMP TABLE _v3_role AS
    SELECT id_role AS viejo, gen_random_uuid() AS nuevo FROM role;
CREATE TEMP TABLE _v3_area AS
    SELECT id_area AS viejo, gen_random_uuid() AS nuevo FROM area;
CREATE TEMP TABLE _v3_users AS
    SELECT id_user AS viejo, gen_random_uuid() AS nuevo FROM users;
CREATE TEMP TABLE _v3_status AS
    SELECT id_status AS viejo, gen_random_uuid() AS nuevo FROM status;
CREATE TEMP TABLE _v3_priority AS
    SELECT id_priority AS viejo, gen_random_uuid() AS nuevo FROM priority;
CREATE TEMP TABLE _v3_support_team AS
    SELECT id_team AS viejo, gen_random_uuid() AS nuevo FROM support_team;
CREATE TEMP TABLE _v3_team_user AS
    SELECT id_team_user AS viejo, gen_random_uuid() AS nuevo FROM team_user;
CREATE TEMP TABLE _v3_category AS
    SELECT id_category AS viejo, gen_random_uuid() AS nuevo FROM category;
CREATE TEMP TABLE _v3_sla_policy AS
    SELECT id_sla_policy AS viejo, gen_random_uuid() AS nuevo FROM sla_policy;
CREATE TEMP TABLE _v3_sla_rule AS
    SELECT id_sla_rule AS viejo, gen_random_uuid() AS nuevo FROM sla_rule;
CREATE TEMP TABLE _v3_ticket AS
    SELECT id_ticket AS viejo, gen_random_uuid() AS nuevo FROM ticket;
CREATE TEMP TABLE _v3_ticket_sla AS
    SELECT id_ticket_sla AS viejo, gen_random_uuid() AS nuevo FROM ticket_sla;
CREATE TEMP TABLE _v3_answer AS
    SELECT id_answer AS viejo, gen_random_uuid() AS nuevo FROM answer;
CREATE TEMP TABLE _v3_attach AS
    SELECT id_attach AS viejo, gen_random_uuid() AS nuevo FROM attach;
CREATE TEMP TABLE _v3_ticket_audit AS
    SELECT id_audit AS viejo, gen_random_uuid() AS nuevo FROM ticket_audit;
CREATE TEMP TABLE _v3_notification_template AS
    SELECT id_template AS viejo, gen_random_uuid() AS nuevo FROM notification_template;
CREATE TEMP TABLE _v3_notification AS
    SELECT id_notification AS viejo, gen_random_uuid() AS nuevo FROM notification;
CREATE TEMP TABLE _v3_sla_escalation AS
    SELECT id_escalation AS viejo, gen_random_uuid() AS nuevo FROM sla_escalation;
CREATE TEMP TABLE _v3_ticket_rating AS
    SELECT id_rating AS viejo, gen_random_uuid() AS nuevo FROM ticket_rating;
CREATE TEMP TABLE _v3_monthly_evaluation AS
    SELECT id_evaluation AS viejo, gen_random_uuid() AS nuevo FROM monthly_evaluation;
CREATE TEMP TABLE _v3_branding_config AS
    SELECT id_branding AS viejo, gen_random_uuid() AS nuevo FROM branding_config;
CREATE TEMP TABLE _v3_sucursal AS
    SELECT id_sucursal AS viejo, gen_random_uuid() AS nuevo FROM sucursal;
CREATE TEMP TABLE _v3_ticket_type AS
    SELECT id_ticket_type AS viejo, gen_random_uuid() AS nuevo FROM ticket_type;
CREATE TEMP TABLE _v3_smtp_config AS
    SELECT id_smtp AS viejo, gen_random_uuid() AS nuevo FROM smtp_config;
CREATE TEMP TABLE _v3_report_config AS
    SELECT id_report AS viejo, gen_random_uuid() AS nuevo FROM report_config;
CREATE TEMP TABLE _v3_task AS
    SELECT id_task AS viejo, gen_random_uuid() AS nuevo FROM task;
CREATE TEMP TABLE _v3_answer_read AS
    SELECT id_answer_read AS viejo, gen_random_uuid() AS nuevo FROM answer_read;
CREATE TEMP TABLE _v3_login_attempt AS
    SELECT id_login_attempt AS viejo, gen_random_uuid() AS nuevo FROM login_attempt;
CREATE TEMP TABLE _v3_refresh_token AS
    SELECT id_refresh_token AS viejo, gen_random_uuid() AS nuevo FROM refresh_token;
CREATE TEMP TABLE _v3_audit_log AS
    SELECT id_audit AS viejo, gen_random_uuid() AS nuevo FROM audit_log;
CREATE TEMP TABLE _v3_ai_config AS
    SELECT id_ai_config AS viejo, gen_random_uuid() AS nuevo FROM ai_config;
CREATE TEMP TABLE _v3_microsoft_graph_config AS
    SELECT id_graph_config AS viejo, gen_random_uuid() AS nuevo FROM microsoft_graph_config;
CREATE TEMP TABLE _v3_graph_config_audit AS
    SELECT id_graph_audit AS viejo, gen_random_uuid() AS nuevo FROM graph_config_audit;
CREATE TEMP TABLE _v3_notification_template_image AS
    SELECT id_image AS viejo, gen_random_uuid() AS nuevo FROM notification_template_image;
CREATE TEMP TABLE _v3_password_reset_token AS
    SELECT id_password_reset_token AS viejo, gen_random_uuid() AS nuevo FROM password_reset_token;
CREATE TEMP TABLE _v3_user_mfa AS
    SELECT id_user_mfa AS viejo, gen_random_uuid() AS nuevo FROM user_mfa;
CREATE TEMP TABLE _v3_mfa_challenge AS
    SELECT id_mfa_challenge AS viejo, gen_random_uuid() AS nuevo FROM mfa_challenge;
CREATE TEMP TABLE _v3_mfa_recovery_code AS
    SELECT id_mfa_recovery AS viejo, gen_random_uuid() AS nuevo FROM mfa_recovery_code;
CREATE TEMP TABLE _v3_workflow AS
    SELECT id_workflow AS viejo, gen_random_uuid() AS nuevo FROM workflow;
CREATE TEMP TABLE _v3_workflow_version AS
    SELECT id_workflow_version AS viejo, gen_random_uuid() AS nuevo FROM workflow_version;
CREATE TEMP TABLE _v3_workflow_execution AS
    SELECT id_workflow_execution AS viejo, gen_random_uuid() AS nuevo FROM workflow_execution;
CREATE TEMP TABLE _v3_workflow_node_run AS
    SELECT id_node_run AS viejo, gen_random_uuid() AS nuevo FROM workflow_node_run;
CREATE TEMP TABLE _v3_workflow_wait AS
    SELECT id_wait AS viejo, gen_random_uuid() AS nuevo FROM workflow_wait;
CREATE TEMP TABLE _v3_workflow_event_dedup AS
    SELECT id_event_dedup AS viejo, gen_random_uuid() AS nuevo FROM workflow_event_dedup;

-- Tablas singleton: uuid FIJO, el mismo que usan las entidades Java.
-- (puede que la fila aun no exista al llegar aqui en una base nueva: en ese
-- caso el mapa queda vacio y el seed de R__datos_base inserta el uuid fijo)
CREATE TEMP TABLE _v3_session_config AS
    SELECT id_session_config AS viejo,
           '00000000-0000-0000-0000-000000000001'::uuid AS nuevo
    FROM session_config;
CREATE TEMP TABLE _v3_routing_config AS
    SELECT id_routing_config AS viejo,
           '00000000-0000-0000-0000-000000000002'::uuid AS nuevo
    FROM routing_config;
CREATE TEMP TABLE _v3_chat_config AS
    SELECT id_chat_config AS viejo,
           '00000000-0000-0000-0000-000000000003'::uuid AS nuevo
    FROM chat_config;
CREATE TEMP TABLE _v3_error_page_config AS
    SELECT id_error_page AS viejo,
           '00000000-0000-0000-0000-000000000004'::uuid AS nuevo
    FROM error_page_config;
CREATE TEMP TABLE _v3_privacy_config AS
    SELECT id_privacy_config AS viejo,
           '00000000-0000-0000-0000-000000000005'::uuid AS nuevo
    FROM privacy_config;
CREATE TEMP TABLE _v3_mfa_setting AS
    SELECT id_mfa_setting AS viejo,
           '00000000-0000-0000-0000-000000000006'::uuid AS nuevo
    FROM mfa_setting;

-- ----------------------------------------------------------------------------
-- 3) CLAVES PRIMARIAS -> uuid
-- ----------------------------------------------------------------------------
-- Para cada tabla: se suelta la PK implicita (se llama <tabla>_pkey), se crea
-- la columna uuid, se rellena con el mapa, se elimina la columna BIGINT (y con
-- ella su IDENTITY) y se renombra. Se restaura NOT NULL, un DEFAULT
-- gen_random_uuid() (para que los INSERT sin id de los seeds sigan funcionando)
-- y la PK con un nombre explicito.
ALTER TABLE role DROP CONSTRAINT IF EXISTS role_pkey;
ALTER TABLE role ADD COLUMN id_role_uuid uuid;
UPDATE role r SET id_role_uuid = m.nuevo FROM _v3_role m WHERE r.id_role = m.viejo;
ALTER TABLE role DROP COLUMN id_role;
ALTER TABLE role RENAME COLUMN id_role_uuid TO id_role;
ALTER TABLE role ALTER COLUMN id_role SET NOT NULL;
ALTER TABLE role ALTER COLUMN id_role SET DEFAULT gen_random_uuid();
ALTER TABLE role ADD CONSTRAINT pk_role PRIMARY KEY (id_role);

ALTER TABLE area DROP CONSTRAINT IF EXISTS area_pkey;
ALTER TABLE area ADD COLUMN id_area_uuid uuid;
UPDATE area a SET id_area_uuid = m.nuevo FROM _v3_area m WHERE a.id_area = m.viejo;
ALTER TABLE area DROP COLUMN id_area;
ALTER TABLE area RENAME COLUMN id_area_uuid TO id_area;
ALTER TABLE area ALTER COLUMN id_area SET NOT NULL;
ALTER TABLE area ALTER COLUMN id_area SET DEFAULT gen_random_uuid();
ALTER TABLE area ADD CONSTRAINT pk_area PRIMARY KEY (id_area);

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE users ADD COLUMN id_user_uuid uuid;
UPDATE users u SET id_user_uuid = m.nuevo FROM _v3_users m WHERE u.id_user = m.viejo;
ALTER TABLE users DROP COLUMN id_user;
ALTER TABLE users RENAME COLUMN id_user_uuid TO id_user;
ALTER TABLE users ALTER COLUMN id_user SET NOT NULL;
ALTER TABLE users ALTER COLUMN id_user SET DEFAULT gen_random_uuid();
ALTER TABLE users ADD CONSTRAINT pk_users PRIMARY KEY (id_user);

ALTER TABLE status DROP CONSTRAINT IF EXISTS status_pkey;
ALTER TABLE status ADD COLUMN id_status_uuid uuid;
UPDATE status s SET id_status_uuid = m.nuevo FROM _v3_status m WHERE s.id_status = m.viejo;
ALTER TABLE status DROP COLUMN id_status;
ALTER TABLE status RENAME COLUMN id_status_uuid TO id_status;
ALTER TABLE status ALTER COLUMN id_status SET NOT NULL;
ALTER TABLE status ALTER COLUMN id_status SET DEFAULT gen_random_uuid();
ALTER TABLE status ADD CONSTRAINT pk_status PRIMARY KEY (id_status);

ALTER TABLE priority DROP CONSTRAINT IF EXISTS priority_pkey;
ALTER TABLE priority ADD COLUMN id_priority_uuid uuid;
UPDATE priority p SET id_priority_uuid = m.nuevo FROM _v3_priority m WHERE p.id_priority = m.viejo;
ALTER TABLE priority DROP COLUMN id_priority;
ALTER TABLE priority RENAME COLUMN id_priority_uuid TO id_priority;
ALTER TABLE priority ALTER COLUMN id_priority SET NOT NULL;
ALTER TABLE priority ALTER COLUMN id_priority SET DEFAULT gen_random_uuid();
ALTER TABLE priority ADD CONSTRAINT pk_priority PRIMARY KEY (id_priority);

ALTER TABLE support_team DROP CONSTRAINT IF EXISTS support_team_pkey;
ALTER TABLE support_team ADD COLUMN id_team_uuid uuid;
UPDATE support_team st SET id_team_uuid = m.nuevo FROM _v3_support_team m WHERE st.id_team = m.viejo;
ALTER TABLE support_team DROP COLUMN id_team;
ALTER TABLE support_team RENAME COLUMN id_team_uuid TO id_team;
ALTER TABLE support_team ALTER COLUMN id_team SET NOT NULL;
ALTER TABLE support_team ALTER COLUMN id_team SET DEFAULT gen_random_uuid();
ALTER TABLE support_team ADD CONSTRAINT pk_support_team PRIMARY KEY (id_team);

ALTER TABLE team_user DROP CONSTRAINT IF EXISTS team_user_pkey;
ALTER TABLE team_user ADD COLUMN id_team_user_uuid uuid;
UPDATE team_user tu SET id_team_user_uuid = m.nuevo FROM _v3_team_user m WHERE tu.id_team_user = m.viejo;
ALTER TABLE team_user DROP COLUMN id_team_user;
ALTER TABLE team_user RENAME COLUMN id_team_user_uuid TO id_team_user;
ALTER TABLE team_user ALTER COLUMN id_team_user SET NOT NULL;
ALTER TABLE team_user ALTER COLUMN id_team_user SET DEFAULT gen_random_uuid();
ALTER TABLE team_user ADD CONSTRAINT pk_team_user PRIMARY KEY (id_team_user);

ALTER TABLE category DROP CONSTRAINT IF EXISTS category_pkey;
ALTER TABLE category ADD COLUMN id_category_uuid uuid;
UPDATE category c SET id_category_uuid = m.nuevo FROM _v3_category m WHERE c.id_category = m.viejo;
ALTER TABLE category DROP COLUMN id_category;
ALTER TABLE category RENAME COLUMN id_category_uuid TO id_category;
ALTER TABLE category ALTER COLUMN id_category SET NOT NULL;
ALTER TABLE category ALTER COLUMN id_category SET DEFAULT gen_random_uuid();
ALTER TABLE category ADD CONSTRAINT pk_category PRIMARY KEY (id_category);

ALTER TABLE sla_policy DROP CONSTRAINT IF EXISTS sla_policy_pkey;
ALTER TABLE sla_policy ADD COLUMN id_sla_policy_uuid uuid;
UPDATE sla_policy sp SET id_sla_policy_uuid = m.nuevo FROM _v3_sla_policy m WHERE sp.id_sla_policy = m.viejo;
ALTER TABLE sla_policy DROP COLUMN id_sla_policy;
ALTER TABLE sla_policy RENAME COLUMN id_sla_policy_uuid TO id_sla_policy;
ALTER TABLE sla_policy ALTER COLUMN id_sla_policy SET NOT NULL;
ALTER TABLE sla_policy ALTER COLUMN id_sla_policy SET DEFAULT gen_random_uuid();
ALTER TABLE sla_policy ADD CONSTRAINT pk_sla_policy PRIMARY KEY (id_sla_policy);

ALTER TABLE sla_rule DROP CONSTRAINT IF EXISTS sla_rule_pkey;
ALTER TABLE sla_rule ADD COLUMN id_sla_rule_uuid uuid;
UPDATE sla_rule sr SET id_sla_rule_uuid = m.nuevo FROM _v3_sla_rule m WHERE sr.id_sla_rule = m.viejo;
ALTER TABLE sla_rule DROP COLUMN id_sla_rule;
ALTER TABLE sla_rule RENAME COLUMN id_sla_rule_uuid TO id_sla_rule;
ALTER TABLE sla_rule ALTER COLUMN id_sla_rule SET NOT NULL;
ALTER TABLE sla_rule ALTER COLUMN id_sla_rule SET DEFAULT gen_random_uuid();
ALTER TABLE sla_rule ADD CONSTRAINT pk_sla_rule PRIMARY KEY (id_sla_rule);

ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_pkey;
ALTER TABLE ticket ADD COLUMN id_ticket_uuid uuid;
UPDATE ticket t SET id_ticket_uuid = m.nuevo FROM _v3_ticket m WHERE t.id_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN id_ticket;
ALTER TABLE ticket RENAME COLUMN id_ticket_uuid TO id_ticket;
ALTER TABLE ticket ALTER COLUMN id_ticket SET NOT NULL;
ALTER TABLE ticket ALTER COLUMN id_ticket SET DEFAULT gen_random_uuid();
ALTER TABLE ticket ADD CONSTRAINT pk_ticket PRIMARY KEY (id_ticket);

ALTER TABLE ticket_sla DROP CONSTRAINT IF EXISTS ticket_sla_pkey;
ALTER TABLE ticket_sla ADD COLUMN id_ticket_sla_uuid uuid;
UPDATE ticket_sla ts SET id_ticket_sla_uuid = m.nuevo FROM _v3_ticket_sla m WHERE ts.id_ticket_sla = m.viejo;
ALTER TABLE ticket_sla DROP COLUMN id_ticket_sla;
ALTER TABLE ticket_sla RENAME COLUMN id_ticket_sla_uuid TO id_ticket_sla;
ALTER TABLE ticket_sla ALTER COLUMN id_ticket_sla SET NOT NULL;
ALTER TABLE ticket_sla ALTER COLUMN id_ticket_sla SET DEFAULT gen_random_uuid();
ALTER TABLE ticket_sla ADD CONSTRAINT pk_ticket_sla PRIMARY KEY (id_ticket_sla);

ALTER TABLE answer DROP CONSTRAINT IF EXISTS answer_pkey;
ALTER TABLE answer ADD COLUMN id_answer_uuid uuid;
UPDATE answer a SET id_answer_uuid = m.nuevo FROM _v3_answer m WHERE a.id_answer = m.viejo;
ALTER TABLE answer DROP COLUMN id_answer;
ALTER TABLE answer RENAME COLUMN id_answer_uuid TO id_answer;
ALTER TABLE answer ALTER COLUMN id_answer SET NOT NULL;
ALTER TABLE answer ALTER COLUMN id_answer SET DEFAULT gen_random_uuid();
ALTER TABLE answer ADD CONSTRAINT pk_answer PRIMARY KEY (id_answer);

ALTER TABLE attach DROP CONSTRAINT IF EXISTS attach_pkey;
ALTER TABLE attach ADD COLUMN id_attach_uuid uuid;
UPDATE attach a SET id_attach_uuid = m.nuevo FROM _v3_attach m WHERE a.id_attach = m.viejo;
ALTER TABLE attach DROP COLUMN id_attach;
ALTER TABLE attach RENAME COLUMN id_attach_uuid TO id_attach;
ALTER TABLE attach ALTER COLUMN id_attach SET NOT NULL;
ALTER TABLE attach ALTER COLUMN id_attach SET DEFAULT gen_random_uuid();
ALTER TABLE attach ADD CONSTRAINT pk_attach PRIMARY KEY (id_attach);

ALTER TABLE ticket_audit DROP CONSTRAINT IF EXISTS ticket_audit_pkey;
ALTER TABLE ticket_audit ADD COLUMN id_audit_uuid uuid;
UPDATE ticket_audit ta SET id_audit_uuid = m.nuevo FROM _v3_ticket_audit m WHERE ta.id_audit = m.viejo;
ALTER TABLE ticket_audit DROP COLUMN id_audit;
ALTER TABLE ticket_audit RENAME COLUMN id_audit_uuid TO id_audit;
ALTER TABLE ticket_audit ALTER COLUMN id_audit SET NOT NULL;
ALTER TABLE ticket_audit ALTER COLUMN id_audit SET DEFAULT gen_random_uuid();
ALTER TABLE ticket_audit ADD CONSTRAINT pk_ticket_audit PRIMARY KEY (id_audit);

ALTER TABLE notification_template DROP CONSTRAINT IF EXISTS notification_template_pkey;
ALTER TABLE notification_template ADD COLUMN id_template_uuid uuid;
UPDATE notification_template nt SET id_template_uuid = m.nuevo FROM _v3_notification_template m WHERE nt.id_template = m.viejo;
ALTER TABLE notification_template DROP COLUMN id_template;
ALTER TABLE notification_template RENAME COLUMN id_template_uuid TO id_template;
ALTER TABLE notification_template ALTER COLUMN id_template SET NOT NULL;
ALTER TABLE notification_template ALTER COLUMN id_template SET DEFAULT gen_random_uuid();
ALTER TABLE notification_template ADD CONSTRAINT pk_notification_template PRIMARY KEY (id_template);

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_pkey;
ALTER TABLE notification ADD COLUMN id_notification_uuid uuid;
UPDATE notification n SET id_notification_uuid = m.nuevo FROM _v3_notification m WHERE n.id_notification = m.viejo;
ALTER TABLE notification DROP COLUMN id_notification;
ALTER TABLE notification RENAME COLUMN id_notification_uuid TO id_notification;
ALTER TABLE notification ALTER COLUMN id_notification SET NOT NULL;
ALTER TABLE notification ALTER COLUMN id_notification SET DEFAULT gen_random_uuid();
ALTER TABLE notification ADD CONSTRAINT pk_notification PRIMARY KEY (id_notification);

ALTER TABLE sla_escalation DROP CONSTRAINT IF EXISTS sla_escalation_pkey;
ALTER TABLE sla_escalation ADD COLUMN id_escalation_uuid uuid;
UPDATE sla_escalation se SET id_escalation_uuid = m.nuevo FROM _v3_sla_escalation m WHERE se.id_escalation = m.viejo;
ALTER TABLE sla_escalation DROP COLUMN id_escalation;
ALTER TABLE sla_escalation RENAME COLUMN id_escalation_uuid TO id_escalation;
ALTER TABLE sla_escalation ALTER COLUMN id_escalation SET NOT NULL;
ALTER TABLE sla_escalation ALTER COLUMN id_escalation SET DEFAULT gen_random_uuid();
ALTER TABLE sla_escalation ADD CONSTRAINT pk_sla_escalation PRIMARY KEY (id_escalation);

ALTER TABLE ticket_rating DROP CONSTRAINT IF EXISTS ticket_rating_pkey;
ALTER TABLE ticket_rating ADD COLUMN id_rating_uuid uuid;
UPDATE ticket_rating tr SET id_rating_uuid = m.nuevo FROM _v3_ticket_rating m WHERE tr.id_rating = m.viejo;
ALTER TABLE ticket_rating DROP COLUMN id_rating;
ALTER TABLE ticket_rating RENAME COLUMN id_rating_uuid TO id_rating;
ALTER TABLE ticket_rating ALTER COLUMN id_rating SET NOT NULL;
ALTER TABLE ticket_rating ALTER COLUMN id_rating SET DEFAULT gen_random_uuid();
ALTER TABLE ticket_rating ADD CONSTRAINT pk_ticket_rating PRIMARY KEY (id_rating);

ALTER TABLE monthly_evaluation DROP CONSTRAINT IF EXISTS monthly_evaluation_pkey;
ALTER TABLE monthly_evaluation ADD COLUMN id_evaluation_uuid uuid;
UPDATE monthly_evaluation me SET id_evaluation_uuid = m.nuevo FROM _v3_monthly_evaluation m WHERE me.id_evaluation = m.viejo;
ALTER TABLE monthly_evaluation DROP COLUMN id_evaluation;
ALTER TABLE monthly_evaluation RENAME COLUMN id_evaluation_uuid TO id_evaluation;
ALTER TABLE monthly_evaluation ALTER COLUMN id_evaluation SET NOT NULL;
ALTER TABLE monthly_evaluation ALTER COLUMN id_evaluation SET DEFAULT gen_random_uuid();
ALTER TABLE monthly_evaluation ADD CONSTRAINT pk_monthly_evaluation PRIMARY KEY (id_evaluation);

ALTER TABLE branding_config DROP CONSTRAINT IF EXISTS branding_config_pkey;
ALTER TABLE branding_config ADD COLUMN id_branding_uuid uuid;
UPDATE branding_config bc SET id_branding_uuid = m.nuevo FROM _v3_branding_config m WHERE bc.id_branding = m.viejo;
ALTER TABLE branding_config DROP COLUMN id_branding;
ALTER TABLE branding_config RENAME COLUMN id_branding_uuid TO id_branding;
ALTER TABLE branding_config ALTER COLUMN id_branding SET NOT NULL;
ALTER TABLE branding_config ALTER COLUMN id_branding SET DEFAULT gen_random_uuid();
ALTER TABLE branding_config ADD CONSTRAINT pk_branding_config PRIMARY KEY (id_branding);

ALTER TABLE sucursal DROP CONSTRAINT IF EXISTS sucursal_pkey;
ALTER TABLE sucursal ADD COLUMN id_sucursal_uuid uuid;
UPDATE sucursal sc SET id_sucursal_uuid = m.nuevo FROM _v3_sucursal m WHERE sc.id_sucursal = m.viejo;
ALTER TABLE sucursal DROP COLUMN id_sucursal;
ALTER TABLE sucursal RENAME COLUMN id_sucursal_uuid TO id_sucursal;
ALTER TABLE sucursal ALTER COLUMN id_sucursal SET NOT NULL;
ALTER TABLE sucursal ALTER COLUMN id_sucursal SET DEFAULT gen_random_uuid();
ALTER TABLE sucursal ADD CONSTRAINT pk_sucursal PRIMARY KEY (id_sucursal);

ALTER TABLE ticket_type DROP CONSTRAINT IF EXISTS ticket_type_pkey;
ALTER TABLE ticket_type ADD COLUMN id_ticket_type_uuid uuid;
UPDATE ticket_type tt SET id_ticket_type_uuid = m.nuevo FROM _v3_ticket_type m WHERE tt.id_ticket_type = m.viejo;
ALTER TABLE ticket_type DROP COLUMN id_ticket_type;
ALTER TABLE ticket_type RENAME COLUMN id_ticket_type_uuid TO id_ticket_type;
ALTER TABLE ticket_type ALTER COLUMN id_ticket_type SET NOT NULL;
ALTER TABLE ticket_type ALTER COLUMN id_ticket_type SET DEFAULT gen_random_uuid();
ALTER TABLE ticket_type ADD CONSTRAINT pk_ticket_type PRIMARY KEY (id_ticket_type);

ALTER TABLE smtp_config DROP CONSTRAINT IF EXISTS smtp_config_pkey;
ALTER TABLE smtp_config ADD COLUMN id_smtp_uuid uuid;
UPDATE smtp_config sc SET id_smtp_uuid = m.nuevo FROM _v3_smtp_config m WHERE sc.id_smtp = m.viejo;
ALTER TABLE smtp_config DROP COLUMN id_smtp;
ALTER TABLE smtp_config RENAME COLUMN id_smtp_uuid TO id_smtp;
ALTER TABLE smtp_config ALTER COLUMN id_smtp SET NOT NULL;
ALTER TABLE smtp_config ALTER COLUMN id_smtp SET DEFAULT gen_random_uuid();
ALTER TABLE smtp_config ADD CONSTRAINT pk_smtp_config PRIMARY KEY (id_smtp);

ALTER TABLE report_config DROP CONSTRAINT IF EXISTS report_config_pkey;
ALTER TABLE report_config ADD COLUMN id_report_uuid uuid;
UPDATE report_config rc SET id_report_uuid = m.nuevo FROM _v3_report_config m WHERE rc.id_report = m.viejo;
ALTER TABLE report_config DROP COLUMN id_report;
ALTER TABLE report_config RENAME COLUMN id_report_uuid TO id_report;
ALTER TABLE report_config ALTER COLUMN id_report SET NOT NULL;
ALTER TABLE report_config ALTER COLUMN id_report SET DEFAULT gen_random_uuid();
ALTER TABLE report_config ADD CONSTRAINT pk_report_config PRIMARY KEY (id_report);

ALTER TABLE task DROP CONSTRAINT IF EXISTS task_pkey;
ALTER TABLE task ADD COLUMN id_task_uuid uuid;
UPDATE task t SET id_task_uuid = m.nuevo FROM _v3_task m WHERE t.id_task = m.viejo;
ALTER TABLE task DROP COLUMN id_task;
ALTER TABLE task RENAME COLUMN id_task_uuid TO id_task;
ALTER TABLE task ALTER COLUMN id_task SET NOT NULL;
ALTER TABLE task ALTER COLUMN id_task SET DEFAULT gen_random_uuid();
ALTER TABLE task ADD CONSTRAINT pk_task PRIMARY KEY (id_task);

ALTER TABLE answer_read DROP CONSTRAINT IF EXISTS answer_read_pkey;
ALTER TABLE answer_read ADD COLUMN id_answer_read_uuid uuid;
UPDATE answer_read ar SET id_answer_read_uuid = m.nuevo FROM _v3_answer_read m WHERE ar.id_answer_read = m.viejo;
ALTER TABLE answer_read DROP COLUMN id_answer_read;
ALTER TABLE answer_read RENAME COLUMN id_answer_read_uuid TO id_answer_read;
ALTER TABLE answer_read ALTER COLUMN id_answer_read SET NOT NULL;
ALTER TABLE answer_read ALTER COLUMN id_answer_read SET DEFAULT gen_random_uuid();
ALTER TABLE answer_read ADD CONSTRAINT pk_answer_read PRIMARY KEY (id_answer_read);

ALTER TABLE login_attempt DROP CONSTRAINT IF EXISTS login_attempt_pkey;
ALTER TABLE login_attempt ADD COLUMN id_login_attempt_uuid uuid;
UPDATE login_attempt la SET id_login_attempt_uuid = m.nuevo FROM _v3_login_attempt m WHERE la.id_login_attempt = m.viejo;
ALTER TABLE login_attempt DROP COLUMN id_login_attempt;
ALTER TABLE login_attempt RENAME COLUMN id_login_attempt_uuid TO id_login_attempt;
ALTER TABLE login_attempt ALTER COLUMN id_login_attempt SET NOT NULL;
ALTER TABLE login_attempt ALTER COLUMN id_login_attempt SET DEFAULT gen_random_uuid();
ALTER TABLE login_attempt ADD CONSTRAINT pk_login_attempt PRIMARY KEY (id_login_attempt);

ALTER TABLE refresh_token DROP CONSTRAINT IF EXISTS refresh_token_pkey;
ALTER TABLE refresh_token ADD COLUMN id_refresh_token_uuid uuid;
UPDATE refresh_token rt SET id_refresh_token_uuid = m.nuevo FROM _v3_refresh_token m WHERE rt.id_refresh_token = m.viejo;
ALTER TABLE refresh_token DROP COLUMN id_refresh_token;
ALTER TABLE refresh_token RENAME COLUMN id_refresh_token_uuid TO id_refresh_token;
ALTER TABLE refresh_token ALTER COLUMN id_refresh_token SET NOT NULL;
ALTER TABLE refresh_token ALTER COLUMN id_refresh_token SET DEFAULT gen_random_uuid();
ALTER TABLE refresh_token ADD CONSTRAINT pk_refresh_token PRIMARY KEY (id_refresh_token);

ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_pkey;
ALTER TABLE audit_log ADD COLUMN id_audit_uuid uuid;
UPDATE audit_log a SET id_audit_uuid = m.nuevo FROM _v3_audit_log m WHERE a.id_audit = m.viejo;
ALTER TABLE audit_log DROP COLUMN id_audit;
ALTER TABLE audit_log RENAME COLUMN id_audit_uuid TO id_audit;
ALTER TABLE audit_log ALTER COLUMN id_audit SET NOT NULL;
ALTER TABLE audit_log ALTER COLUMN id_audit SET DEFAULT gen_random_uuid();
ALTER TABLE audit_log ADD CONSTRAINT pk_audit_log PRIMARY KEY (id_audit);

ALTER TABLE ai_config DROP CONSTRAINT IF EXISTS ai_config_pkey;
ALTER TABLE ai_config ADD COLUMN id_ai_config_uuid uuid;
UPDATE ai_config ac SET id_ai_config_uuid = m.nuevo FROM _v3_ai_config m WHERE ac.id_ai_config = m.viejo;
ALTER TABLE ai_config DROP COLUMN id_ai_config;
ALTER TABLE ai_config RENAME COLUMN id_ai_config_uuid TO id_ai_config;
ALTER TABLE ai_config ALTER COLUMN id_ai_config SET NOT NULL;
ALTER TABLE ai_config ALTER COLUMN id_ai_config SET DEFAULT gen_random_uuid();
ALTER TABLE ai_config ADD CONSTRAINT pk_ai_config PRIMARY KEY (id_ai_config);

ALTER TABLE microsoft_graph_config DROP CONSTRAINT IF EXISTS microsoft_graph_config_pkey;
ALTER TABLE microsoft_graph_config ADD COLUMN id_graph_config_uuid uuid;
UPDATE microsoft_graph_config mgc SET id_graph_config_uuid = m.nuevo FROM _v3_microsoft_graph_config m WHERE mgc.id_graph_config = m.viejo;
ALTER TABLE microsoft_graph_config DROP COLUMN id_graph_config;
ALTER TABLE microsoft_graph_config RENAME COLUMN id_graph_config_uuid TO id_graph_config;
ALTER TABLE microsoft_graph_config ALTER COLUMN id_graph_config SET NOT NULL;
ALTER TABLE microsoft_graph_config ALTER COLUMN id_graph_config SET DEFAULT gen_random_uuid();
ALTER TABLE microsoft_graph_config ADD CONSTRAINT pk_microsoft_graph_config PRIMARY KEY (id_graph_config);

ALTER TABLE graph_config_audit DROP CONSTRAINT IF EXISTS graph_config_audit_pkey;
ALTER TABLE graph_config_audit ADD COLUMN id_graph_audit_uuid uuid;
UPDATE graph_config_audit gca SET id_graph_audit_uuid = m.nuevo FROM _v3_graph_config_audit m WHERE gca.id_graph_audit = m.viejo;
ALTER TABLE graph_config_audit DROP COLUMN id_graph_audit;
ALTER TABLE graph_config_audit RENAME COLUMN id_graph_audit_uuid TO id_graph_audit;
ALTER TABLE graph_config_audit ALTER COLUMN id_graph_audit SET NOT NULL;
ALTER TABLE graph_config_audit ALTER COLUMN id_graph_audit SET DEFAULT gen_random_uuid();
ALTER TABLE graph_config_audit ADD CONSTRAINT pk_graph_config_audit PRIMARY KEY (id_graph_audit);

ALTER TABLE notification_template_image DROP CONSTRAINT IF EXISTS notification_template_image_pkey;
ALTER TABLE notification_template_image ADD COLUMN id_image_uuid uuid;
UPDATE notification_template_image nti SET id_image_uuid = m.nuevo FROM _v3_notification_template_image m WHERE nti.id_image = m.viejo;
ALTER TABLE notification_template_image DROP COLUMN id_image;
ALTER TABLE notification_template_image RENAME COLUMN id_image_uuid TO id_image;
ALTER TABLE notification_template_image ALTER COLUMN id_image SET NOT NULL;
ALTER TABLE notification_template_image ALTER COLUMN id_image SET DEFAULT gen_random_uuid();
ALTER TABLE notification_template_image ADD CONSTRAINT pk_notification_template_image PRIMARY KEY (id_image);

ALTER TABLE password_reset_token DROP CONSTRAINT IF EXISTS password_reset_token_pkey;
ALTER TABLE password_reset_token ADD COLUMN id_password_reset_token_uuid uuid;
UPDATE password_reset_token prt SET id_password_reset_token_uuid = m.nuevo FROM _v3_password_reset_token m WHERE prt.id_password_reset_token = m.viejo;
ALTER TABLE password_reset_token DROP COLUMN id_password_reset_token;
ALTER TABLE password_reset_token RENAME COLUMN id_password_reset_token_uuid TO id_password_reset_token;
ALTER TABLE password_reset_token ALTER COLUMN id_password_reset_token SET NOT NULL;
ALTER TABLE password_reset_token ALTER COLUMN id_password_reset_token SET DEFAULT gen_random_uuid();
ALTER TABLE password_reset_token ADD CONSTRAINT pk_password_reset_token PRIMARY KEY (id_password_reset_token);

ALTER TABLE user_mfa DROP CONSTRAINT IF EXISTS user_mfa_pkey;
ALTER TABLE user_mfa ADD COLUMN id_user_mfa_uuid uuid;
UPDATE user_mfa um SET id_user_mfa_uuid = m.nuevo FROM _v3_user_mfa m WHERE um.id_user_mfa = m.viejo;
ALTER TABLE user_mfa DROP COLUMN id_user_mfa;
ALTER TABLE user_mfa RENAME COLUMN id_user_mfa_uuid TO id_user_mfa;
ALTER TABLE user_mfa ALTER COLUMN id_user_mfa SET NOT NULL;
ALTER TABLE user_mfa ALTER COLUMN id_user_mfa SET DEFAULT gen_random_uuid();
ALTER TABLE user_mfa ADD CONSTRAINT pk_user_mfa PRIMARY KEY (id_user_mfa);

ALTER TABLE mfa_challenge DROP CONSTRAINT IF EXISTS mfa_challenge_pkey;
ALTER TABLE mfa_challenge ADD COLUMN id_mfa_challenge_uuid uuid;
UPDATE mfa_challenge mc SET id_mfa_challenge_uuid = m.nuevo FROM _v3_mfa_challenge m WHERE mc.id_mfa_challenge = m.viejo;
ALTER TABLE mfa_challenge DROP COLUMN id_mfa_challenge;
ALTER TABLE mfa_challenge RENAME COLUMN id_mfa_challenge_uuid TO id_mfa_challenge;
ALTER TABLE mfa_challenge ALTER COLUMN id_mfa_challenge SET NOT NULL;
ALTER TABLE mfa_challenge ALTER COLUMN id_mfa_challenge SET DEFAULT gen_random_uuid();
ALTER TABLE mfa_challenge ADD CONSTRAINT pk_mfa_challenge PRIMARY KEY (id_mfa_challenge);

ALTER TABLE mfa_recovery_code DROP CONSTRAINT IF EXISTS mfa_recovery_code_pkey;
ALTER TABLE mfa_recovery_code ADD COLUMN id_mfa_recovery_uuid uuid;
UPDATE mfa_recovery_code mrc SET id_mfa_recovery_uuid = m.nuevo FROM _v3_mfa_recovery_code m WHERE mrc.id_mfa_recovery = m.viejo;
ALTER TABLE mfa_recovery_code DROP COLUMN id_mfa_recovery;
ALTER TABLE mfa_recovery_code RENAME COLUMN id_mfa_recovery_uuid TO id_mfa_recovery;
ALTER TABLE mfa_recovery_code ALTER COLUMN id_mfa_recovery SET NOT NULL;
ALTER TABLE mfa_recovery_code ALTER COLUMN id_mfa_recovery SET DEFAULT gen_random_uuid();
ALTER TABLE mfa_recovery_code ADD CONSTRAINT pk_mfa_recovery_code PRIMARY KEY (id_mfa_recovery);

ALTER TABLE workflow DROP CONSTRAINT IF EXISTS workflow_pkey;
ALTER TABLE workflow ADD COLUMN id_workflow_uuid uuid;
UPDATE workflow w SET id_workflow_uuid = m.nuevo FROM _v3_workflow m WHERE w.id_workflow = m.viejo;
ALTER TABLE workflow DROP COLUMN id_workflow;
ALTER TABLE workflow RENAME COLUMN id_workflow_uuid TO id_workflow;
ALTER TABLE workflow ALTER COLUMN id_workflow SET NOT NULL;
ALTER TABLE workflow ALTER COLUMN id_workflow SET DEFAULT gen_random_uuid();
ALTER TABLE workflow ADD CONSTRAINT pk_workflow PRIMARY KEY (id_workflow);

ALTER TABLE workflow_version DROP CONSTRAINT IF EXISTS workflow_version_pkey;
ALTER TABLE workflow_version ADD COLUMN id_workflow_version_uuid uuid;
UPDATE workflow_version wv SET id_workflow_version_uuid = m.nuevo FROM _v3_workflow_version m WHERE wv.id_workflow_version = m.viejo;
ALTER TABLE workflow_version DROP COLUMN id_workflow_version;
ALTER TABLE workflow_version RENAME COLUMN id_workflow_version_uuid TO id_workflow_version;
ALTER TABLE workflow_version ALTER COLUMN id_workflow_version SET NOT NULL;
ALTER TABLE workflow_version ALTER COLUMN id_workflow_version SET DEFAULT gen_random_uuid();
ALTER TABLE workflow_version ADD CONSTRAINT pk_workflow_version PRIMARY KEY (id_workflow_version);

ALTER TABLE workflow_execution DROP CONSTRAINT IF EXISTS workflow_execution_pkey;
ALTER TABLE workflow_execution ADD COLUMN id_workflow_execution_uuid uuid;
UPDATE workflow_execution we SET id_workflow_execution_uuid = m.nuevo FROM _v3_workflow_execution m WHERE we.id_workflow_execution = m.viejo;
ALTER TABLE workflow_execution DROP COLUMN id_workflow_execution;
ALTER TABLE workflow_execution RENAME COLUMN id_workflow_execution_uuid TO id_workflow_execution;
ALTER TABLE workflow_execution ALTER COLUMN id_workflow_execution SET NOT NULL;
ALTER TABLE workflow_execution ALTER COLUMN id_workflow_execution SET DEFAULT gen_random_uuid();
ALTER TABLE workflow_execution ADD CONSTRAINT pk_workflow_execution PRIMARY KEY (id_workflow_execution);

ALTER TABLE workflow_node_run DROP CONSTRAINT IF EXISTS workflow_node_run_pkey;
ALTER TABLE workflow_node_run ADD COLUMN id_node_run_uuid uuid;
UPDATE workflow_node_run wnr SET id_node_run_uuid = m.nuevo FROM _v3_workflow_node_run m WHERE wnr.id_node_run = m.viejo;
ALTER TABLE workflow_node_run DROP COLUMN id_node_run;
ALTER TABLE workflow_node_run RENAME COLUMN id_node_run_uuid TO id_node_run;
ALTER TABLE workflow_node_run ALTER COLUMN id_node_run SET NOT NULL;
ALTER TABLE workflow_node_run ALTER COLUMN id_node_run SET DEFAULT gen_random_uuid();
ALTER TABLE workflow_node_run ADD CONSTRAINT pk_workflow_node_run PRIMARY KEY (id_node_run);

ALTER TABLE workflow_wait DROP CONSTRAINT IF EXISTS workflow_wait_pkey;
ALTER TABLE workflow_wait ADD COLUMN id_wait_uuid uuid;
UPDATE workflow_wait ww SET id_wait_uuid = m.nuevo FROM _v3_workflow_wait m WHERE ww.id_wait = m.viejo;
ALTER TABLE workflow_wait DROP COLUMN id_wait;
ALTER TABLE workflow_wait RENAME COLUMN id_wait_uuid TO id_wait;
ALTER TABLE workflow_wait ALTER COLUMN id_wait SET NOT NULL;
ALTER TABLE workflow_wait ALTER COLUMN id_wait SET DEFAULT gen_random_uuid();
ALTER TABLE workflow_wait ADD CONSTRAINT pk_workflow_wait PRIMARY KEY (id_wait);

ALTER TABLE workflow_event_dedup DROP CONSTRAINT IF EXISTS workflow_event_dedup_pkey;
ALTER TABLE workflow_event_dedup ADD COLUMN id_event_dedup_uuid uuid;
UPDATE workflow_event_dedup wed SET id_event_dedup_uuid = m.nuevo FROM _v3_workflow_event_dedup m WHERE wed.id_event_dedup = m.viejo;
ALTER TABLE workflow_event_dedup DROP COLUMN id_event_dedup;
ALTER TABLE workflow_event_dedup RENAME COLUMN id_event_dedup_uuid TO id_event_dedup;
ALTER TABLE workflow_event_dedup ALTER COLUMN id_event_dedup SET NOT NULL;
ALTER TABLE workflow_event_dedup ALTER COLUMN id_event_dedup SET DEFAULT gen_random_uuid();
ALTER TABLE workflow_event_dedup ADD CONSTRAINT pk_workflow_event_dedup PRIMARY KEY (id_event_dedup);

-- Tablas singleton: uuid FIJO. Al convertir una sola columna sin IDENTITY basta
-- con cambiar el tipo directamente, pero se usa el mismo patron por uniformidad
-- (y porque el CHECK de fila unica se repone con el valor nuevo al final).
ALTER TABLE session_config DROP CONSTRAINT IF EXISTS session_config_pkey;
ALTER TABLE session_config ADD COLUMN id_session_config_uuid uuid;
UPDATE session_config sc SET id_session_config_uuid = m.nuevo FROM _v3_session_config m WHERE sc.id_session_config = m.viejo;
ALTER TABLE session_config DROP COLUMN id_session_config;
ALTER TABLE session_config RENAME COLUMN id_session_config_uuid TO id_session_config;
ALTER TABLE session_config ALTER COLUMN id_session_config SET NOT NULL;
ALTER TABLE session_config ALTER COLUMN id_session_config SET DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE session_config ADD CONSTRAINT pk_session_config PRIMARY KEY (id_session_config);

ALTER TABLE routing_config DROP CONSTRAINT IF EXISTS routing_config_pkey;
ALTER TABLE routing_config ADD COLUMN id_routing_config_uuid uuid;
UPDATE routing_config rc SET id_routing_config_uuid = m.nuevo FROM _v3_routing_config m WHERE rc.id_routing_config = m.viejo;
ALTER TABLE routing_config DROP COLUMN id_routing_config;
ALTER TABLE routing_config RENAME COLUMN id_routing_config_uuid TO id_routing_config;
ALTER TABLE routing_config ALTER COLUMN id_routing_config SET NOT NULL;
ALTER TABLE routing_config ALTER COLUMN id_routing_config SET DEFAULT '00000000-0000-0000-0000-000000000002';
ALTER TABLE routing_config ADD CONSTRAINT pk_routing_config PRIMARY KEY (id_routing_config);

ALTER TABLE chat_config DROP CONSTRAINT IF EXISTS chat_config_pkey;
ALTER TABLE chat_config ADD COLUMN id_chat_config_uuid uuid;
UPDATE chat_config cc SET id_chat_config_uuid = m.nuevo FROM _v3_chat_config m WHERE cc.id_chat_config = m.viejo;
ALTER TABLE chat_config DROP COLUMN id_chat_config;
ALTER TABLE chat_config RENAME COLUMN id_chat_config_uuid TO id_chat_config;
ALTER TABLE chat_config ALTER COLUMN id_chat_config SET NOT NULL;
ALTER TABLE chat_config ALTER COLUMN id_chat_config SET DEFAULT '00000000-0000-0000-0000-000000000003';
ALTER TABLE chat_config ADD CONSTRAINT pk_chat_config PRIMARY KEY (id_chat_config);

ALTER TABLE error_page_config DROP CONSTRAINT IF EXISTS error_page_config_pkey;
ALTER TABLE error_page_config ADD COLUMN id_error_page_uuid uuid;
UPDATE error_page_config epc SET id_error_page_uuid = m.nuevo FROM _v3_error_page_config m WHERE epc.id_error_page = m.viejo;
ALTER TABLE error_page_config DROP COLUMN id_error_page;
ALTER TABLE error_page_config RENAME COLUMN id_error_page_uuid TO id_error_page;
ALTER TABLE error_page_config ALTER COLUMN id_error_page SET NOT NULL;
ALTER TABLE error_page_config ALTER COLUMN id_error_page SET DEFAULT '00000000-0000-0000-0000-000000000004';
ALTER TABLE error_page_config ADD CONSTRAINT pk_error_page_config PRIMARY KEY (id_error_page);

ALTER TABLE privacy_config DROP CONSTRAINT IF EXISTS privacy_config_pkey;
ALTER TABLE privacy_config ADD COLUMN id_privacy_config_uuid uuid;
UPDATE privacy_config pc SET id_privacy_config_uuid = m.nuevo FROM _v3_privacy_config m WHERE pc.id_privacy_config = m.viejo;
ALTER TABLE privacy_config DROP COLUMN id_privacy_config;
ALTER TABLE privacy_config RENAME COLUMN id_privacy_config_uuid TO id_privacy_config;
ALTER TABLE privacy_config ALTER COLUMN id_privacy_config SET NOT NULL;
ALTER TABLE privacy_config ALTER COLUMN id_privacy_config SET DEFAULT '00000000-0000-0000-0000-000000000005';
ALTER TABLE privacy_config ADD CONSTRAINT pk_privacy_config PRIMARY KEY (id_privacy_config);

ALTER TABLE mfa_setting DROP CONSTRAINT IF EXISTS mfa_setting_pkey;
ALTER TABLE mfa_setting ADD COLUMN id_mfa_setting_uuid uuid;
UPDATE mfa_setting ms SET id_mfa_setting_uuid = m.nuevo FROM _v3_mfa_setting m WHERE ms.id_mfa_setting = m.viejo;
ALTER TABLE mfa_setting DROP COLUMN id_mfa_setting;
ALTER TABLE mfa_setting RENAME COLUMN id_mfa_setting_uuid TO id_mfa_setting;
ALTER TABLE mfa_setting ALTER COLUMN id_mfa_setting SET NOT NULL;
ALTER TABLE mfa_setting ALTER COLUMN id_mfa_setting SET DEFAULT '00000000-0000-0000-0000-000000000006';
ALTER TABLE mfa_setting ADD CONSTRAINT pk_mfa_setting PRIMARY KEY (id_mfa_setting);

-- ----------------------------------------------------------------------------
-- 4) COLUMNAS DE REFERENCIA -> uuid
-- ----------------------------------------------------------------------------
-- Misma tecnica que las PK, pero el mapa viene del padre. Se repite el mismo
-- NOT NULL que tenia la columna legacy (pierde sus restricciones al soltarse).
-- ----------------------------------------------------------------------------
-- users
ALTER TABLE users ADD COLUMN rol_user_uuid uuid;
UPDATE users u SET rol_user_uuid = m.nuevo FROM _v3_role m WHERE u.rol_user = m.viejo;
ALTER TABLE users DROP COLUMN rol_user;
ALTER TABLE users RENAME COLUMN rol_user_uuid TO rol_user;
ALTER TABLE users ALTER COLUMN rol_user SET NOT NULL;

ALTER TABLE users ADD COLUMN area_id_uuid uuid;
UPDATE users u SET area_id_uuid = m.nuevo FROM _v3_area m WHERE u.area_id = m.viejo;
ALTER TABLE users DROP COLUMN area_id;
ALTER TABLE users RENAME COLUMN area_id_uuid TO area_id;
ALTER TABLE users ALTER COLUMN area_id SET NOT NULL;

ALTER TABLE users ADD COLUMN sucursal_id_uuid uuid;
UPDATE users u SET sucursal_id_uuid = m.nuevo FROM _v3_sucursal m WHERE u.sucursal_id = m.viejo;
ALTER TABLE users DROP COLUMN sucursal_id;
ALTER TABLE users RENAME COLUMN sucursal_id_uuid TO sucursal_id;

-- team_user
ALTER TABLE team_user ADD COLUMN team_id_uuid uuid;
UPDATE team_user tu SET team_id_uuid = m.nuevo FROM _v3_support_team m WHERE tu.team_id = m.viejo;
ALTER TABLE team_user DROP COLUMN team_id;
ALTER TABLE team_user RENAME COLUMN team_id_uuid TO team_id;
ALTER TABLE team_user ALTER COLUMN team_id SET NOT NULL;

ALTER TABLE team_user ADD COLUMN user_id_uuid uuid;
UPDATE team_user tu SET user_id_uuid = m.nuevo FROM _v3_users m WHERE tu.user_id = m.viejo;
ALTER TABLE team_user DROP COLUMN user_id;
ALTER TABLE team_user RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE team_user ALTER COLUMN user_id SET NOT NULL;

-- category
ALTER TABLE category ADD COLUMN team_category_uuid uuid;
UPDATE category c SET team_category_uuid = m.nuevo FROM _v3_support_team m WHERE c.team_category = m.viejo;
ALTER TABLE category DROP COLUMN team_category;
ALTER TABLE category RENAME COLUMN team_category_uuid TO team_category;

-- sla_rule
ALTER TABLE sla_rule ADD COLUMN sla_policy_id_uuid uuid;
UPDATE sla_rule sr SET sla_policy_id_uuid = m.nuevo FROM _v3_sla_policy m WHERE sr.sla_policy_id = m.viejo;
ALTER TABLE sla_rule DROP COLUMN sla_policy_id;
ALTER TABLE sla_rule RENAME COLUMN sla_policy_id_uuid TO sla_policy_id;
ALTER TABLE sla_rule ALTER COLUMN sla_policy_id SET NOT NULL;

ALTER TABLE sla_rule ADD COLUMN area_id_uuid uuid;
UPDATE sla_rule sr SET area_id_uuid = m.nuevo FROM _v3_area m WHERE sr.area_id = m.viejo;
ALTER TABLE sla_rule DROP COLUMN area_id;
ALTER TABLE sla_rule RENAME COLUMN area_id_uuid TO area_id;
ALTER TABLE sla_rule ALTER COLUMN area_id SET NOT NULL;

ALTER TABLE sla_rule ADD COLUMN priority_id_uuid uuid;
UPDATE sla_rule sr SET priority_id_uuid = m.nuevo FROM _v3_priority m WHERE sr.priority_id = m.viejo;
ALTER TABLE sla_rule DROP COLUMN priority_id;
ALTER TABLE sla_rule RENAME COLUMN priority_id_uuid TO priority_id;
ALTER TABLE sla_rule ALTER COLUMN priority_id SET NOT NULL;

ALTER TABLE sla_rule ADD COLUMN category_id_uuid uuid;
UPDATE sla_rule sr SET category_id_uuid = m.nuevo FROM _v3_category m WHERE sr.category_id = m.viejo;
ALTER TABLE sla_rule DROP COLUMN category_id;
ALTER TABLE sla_rule RENAME COLUMN category_id_uuid TO category_id;

ALTER TABLE sla_rule ADD COLUMN type_ticket_uuid uuid;
UPDATE sla_rule sr SET type_ticket_uuid = m.nuevo FROM _v3_ticket_type m WHERE sr.type_ticket = m.viejo;
ALTER TABLE sla_rule DROP COLUMN type_ticket;
ALTER TABLE sla_rule RENAME COLUMN type_ticket_uuid TO type_ticket;

-- ticket
ALTER TABLE ticket ADD COLUMN user_ticket_uuid uuid;
UPDATE ticket t SET user_ticket_uuid = m.nuevo FROM _v3_users m WHERE t.user_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN user_ticket;
ALTER TABLE ticket RENAME COLUMN user_ticket_uuid TO user_ticket;
ALTER TABLE ticket ALTER COLUMN user_ticket SET NOT NULL;

ALTER TABLE ticket ADD COLUMN agent_ticket_uuid uuid;
UPDATE ticket t SET agent_ticket_uuid = m.nuevo FROM _v3_users m WHERE t.agent_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN agent_ticket;
ALTER TABLE ticket RENAME COLUMN agent_ticket_uuid TO agent_ticket;

ALTER TABLE ticket ADD COLUMN team_ticket_uuid uuid;
UPDATE ticket t SET team_ticket_uuid = m.nuevo FROM _v3_support_team m WHERE t.team_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN team_ticket;
ALTER TABLE ticket RENAME COLUMN team_ticket_uuid TO team_ticket;

ALTER TABLE ticket ADD COLUMN area_ticket_uuid uuid;
UPDATE ticket t SET area_ticket_uuid = m.nuevo FROM _v3_area m WHERE t.area_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN area_ticket;
ALTER TABLE ticket RENAME COLUMN area_ticket_uuid TO area_ticket;
ALTER TABLE ticket ALTER COLUMN area_ticket SET NOT NULL;

ALTER TABLE ticket ADD COLUMN category_ticket_uuid uuid;
UPDATE ticket t SET category_ticket_uuid = m.nuevo FROM _v3_category m WHERE t.category_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN category_ticket;
ALTER TABLE ticket RENAME COLUMN category_ticket_uuid TO category_ticket;
ALTER TABLE ticket ALTER COLUMN category_ticket SET NOT NULL;

ALTER TABLE ticket ADD COLUMN status_ticket_uuid uuid;
UPDATE ticket t SET status_ticket_uuid = m.nuevo FROM _v3_status m WHERE t.status_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN status_ticket;
ALTER TABLE ticket RENAME COLUMN status_ticket_uuid TO status_ticket;
ALTER TABLE ticket ALTER COLUMN status_ticket SET NOT NULL;

ALTER TABLE ticket ADD COLUMN priority_ticket_uuid uuid;
UPDATE ticket t SET priority_ticket_uuid = m.nuevo FROM _v3_priority m WHERE t.priority_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN priority_ticket;
ALTER TABLE ticket RENAME COLUMN priority_ticket_uuid TO priority_ticket;
ALTER TABLE ticket ALTER COLUMN priority_ticket SET NOT NULL;

ALTER TABLE ticket ADD COLUMN sucursal_id_uuid uuid;
UPDATE ticket t SET sucursal_id_uuid = m.nuevo FROM _v3_sucursal m WHERE t.sucursal_id = m.viejo;
ALTER TABLE ticket DROP COLUMN sucursal_id;
ALTER TABLE ticket RENAME COLUMN sucursal_id_uuid TO sucursal_id;

ALTER TABLE ticket ADD COLUMN type_ticket_uuid uuid;
UPDATE ticket t SET type_ticket_uuid = m.nuevo FROM _v3_ticket_type m WHERE t.type_ticket = m.viejo;
ALTER TABLE ticket DROP COLUMN type_ticket;
ALTER TABLE ticket RENAME COLUMN type_ticket_uuid TO type_ticket;

-- ticket_sla
ALTER TABLE ticket_sla ADD COLUMN ticket_id_uuid uuid;
UPDATE ticket_sla ts SET ticket_id_uuid = m.nuevo FROM _v3_ticket m WHERE ts.ticket_id = m.viejo;
ALTER TABLE ticket_sla DROP COLUMN ticket_id;
ALTER TABLE ticket_sla RENAME COLUMN ticket_id_uuid TO ticket_id;
ALTER TABLE ticket_sla ALTER COLUMN ticket_id SET NOT NULL;

ALTER TABLE ticket_sla ADD COLUMN sla_policy_id_uuid uuid;
UPDATE ticket_sla ts SET sla_policy_id_uuid = m.nuevo FROM _v3_sla_policy m WHERE ts.sla_policy_id = m.viejo;
ALTER TABLE ticket_sla DROP COLUMN sla_policy_id;
ALTER TABLE ticket_sla RENAME COLUMN sla_policy_id_uuid TO sla_policy_id;
ALTER TABLE ticket_sla ALTER COLUMN sla_policy_id SET NOT NULL;

-- answer
ALTER TABLE answer ADD COLUMN ticket_answer_uuid uuid;
UPDATE answer a SET ticket_answer_uuid = m.nuevo FROM _v3_ticket m WHERE a.ticket_answer = m.viejo;
ALTER TABLE answer DROP COLUMN ticket_answer;
ALTER TABLE answer RENAME COLUMN ticket_answer_uuid TO ticket_answer;
ALTER TABLE answer ALTER COLUMN ticket_answer SET NOT NULL;

ALTER TABLE answer ADD COLUMN user_answer_uuid uuid;
UPDATE answer a SET user_answer_uuid = m.nuevo FROM _v3_users m WHERE a.user_answer = m.viejo;
ALTER TABLE answer DROP COLUMN user_answer;
ALTER TABLE answer RENAME COLUMN user_answer_uuid TO user_answer;
ALTER TABLE answer ALTER COLUMN user_answer SET NOT NULL;

-- attach
ALTER TABLE attach ADD COLUMN ticket_attach_uuid uuid;
UPDATE attach a SET ticket_attach_uuid = m.nuevo FROM _v3_ticket m WHERE a.ticket_attach = m.viejo;
ALTER TABLE attach DROP COLUMN ticket_attach;
ALTER TABLE attach RENAME COLUMN ticket_attach_uuid TO ticket_attach;
ALTER TABLE attach ALTER COLUMN ticket_attach SET NOT NULL;

ALTER TABLE attach ADD COLUMN answer_attach_uuid uuid;
UPDATE attach a SET answer_attach_uuid = m.nuevo FROM _v3_answer m WHERE a.answer_attach = m.viejo;
ALTER TABLE attach DROP COLUMN answer_attach;
ALTER TABLE attach RENAME COLUMN answer_attach_uuid TO answer_attach;

-- ticket_audit
ALTER TABLE ticket_audit ADD COLUMN ticket_id_uuid uuid;
UPDATE ticket_audit ta SET ticket_id_uuid = m.nuevo FROM _v3_ticket m WHERE ta.ticket_id = m.viejo;
ALTER TABLE ticket_audit DROP COLUMN ticket_id;
ALTER TABLE ticket_audit RENAME COLUMN ticket_id_uuid TO ticket_id;
ALTER TABLE ticket_audit ALTER COLUMN ticket_id SET NOT NULL;

ALTER TABLE ticket_audit ADD COLUMN user_id_uuid uuid;
UPDATE ticket_audit ta SET user_id_uuid = m.nuevo FROM _v3_users m WHERE ta.user_id = m.viejo;
ALTER TABLE ticket_audit DROP COLUMN user_id;
ALTER TABLE ticket_audit RENAME COLUMN user_id_uuid TO user_id;

-- notification
ALTER TABLE notification ADD COLUMN ticket_id_uuid uuid;
UPDATE notification n SET ticket_id_uuid = m.nuevo FROM _v3_ticket m WHERE n.ticket_id = m.viejo;
ALTER TABLE notification DROP COLUMN ticket_id;
ALTER TABLE notification RENAME COLUMN ticket_id_uuid TO ticket_id;

ALTER TABLE notification ADD COLUMN user_id_uuid uuid;
UPDATE notification n SET user_id_uuid = m.nuevo FROM _v3_users m WHERE n.user_id = m.viejo;
ALTER TABLE notification DROP COLUMN user_id;
ALTER TABLE notification RENAME COLUMN user_id_uuid TO user_id;

-- sla_escalation
ALTER TABLE sla_escalation ADD COLUMN sla_policy_id_uuid uuid;
UPDATE sla_escalation se SET sla_policy_id_uuid = m.nuevo FROM _v3_sla_policy m WHERE se.sla_policy_id = m.viejo;
ALTER TABLE sla_escalation DROP COLUMN sla_policy_id;
ALTER TABLE sla_escalation RENAME COLUMN sla_policy_id_uuid TO sla_policy_id;
ALTER TABLE sla_escalation ALTER COLUMN sla_policy_id SET NOT NULL;

ALTER TABLE sla_escalation ADD COLUMN destination_team_id_uuid uuid;
UPDATE sla_escalation se SET destination_team_id_uuid = m.nuevo FROM _v3_support_team m WHERE se.destination_team_id = m.viejo;
ALTER TABLE sla_escalation DROP COLUMN destination_team_id;
ALTER TABLE sla_escalation RENAME COLUMN destination_team_id_uuid TO destination_team_id;

ALTER TABLE sla_escalation ADD COLUMN destination_user_id_uuid uuid;
UPDATE sla_escalation se SET destination_user_id_uuid = m.nuevo FROM _v3_users m WHERE se.destination_user_id = m.viejo;
ALTER TABLE sla_escalation DROP COLUMN destination_user_id;
ALTER TABLE sla_escalation RENAME COLUMN destination_user_id_uuid TO destination_user_id;

ALTER TABLE sla_escalation ADD COLUMN notification_template_id_uuid uuid;
UPDATE sla_escalation se SET notification_template_id_uuid = m.nuevo FROM _v3_notification_template m WHERE se.notification_template_id = m.viejo;
ALTER TABLE sla_escalation DROP COLUMN notification_template_id;
ALTER TABLE sla_escalation RENAME COLUMN notification_template_id_uuid TO notification_template_id;

-- ticket_rating
ALTER TABLE ticket_rating ADD COLUMN ticket_id_uuid uuid;
UPDATE ticket_rating tr SET ticket_id_uuid = m.nuevo FROM _v3_ticket m WHERE tr.ticket_id = m.viejo;
ALTER TABLE ticket_rating DROP COLUMN ticket_id;
ALTER TABLE ticket_rating RENAME COLUMN ticket_id_uuid TO ticket_id;
ALTER TABLE ticket_rating ALTER COLUMN ticket_id SET NOT NULL;

ALTER TABLE ticket_rating ADD COLUMN user_id_uuid uuid;
UPDATE ticket_rating tr SET user_id_uuid = m.nuevo FROM _v3_users m WHERE tr.user_id = m.viejo;
ALTER TABLE ticket_rating DROP COLUMN user_id;
ALTER TABLE ticket_rating RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE ticket_rating ALTER COLUMN user_id SET NOT NULL;

-- monthly_evaluation
ALTER TABLE monthly_evaluation ADD COLUMN agent_id_uuid uuid;
UPDATE monthly_evaluation me SET agent_id_uuid = m.nuevo FROM _v3_users m WHERE me.agent_id = m.viejo;
ALTER TABLE monthly_evaluation DROP COLUMN agent_id;
ALTER TABLE monthly_evaluation RENAME COLUMN agent_id_uuid TO agent_id;
ALTER TABLE monthly_evaluation ALTER COLUMN agent_id SET NOT NULL;

ALTER TABLE monthly_evaluation ADD COLUMN evaluated_by_uuid uuid;
UPDATE monthly_evaluation me SET evaluated_by_uuid = m.nuevo FROM _v3_users m WHERE me.evaluated_by = m.viejo;
ALTER TABLE monthly_evaluation DROP COLUMN evaluated_by;
ALTER TABLE monthly_evaluation RENAME COLUMN evaluated_by_uuid TO evaluated_by;

-- task
ALTER TABLE task ADD COLUMN assigned_user_uuid uuid;
UPDATE task t SET assigned_user_uuid = m.nuevo FROM _v3_users m WHERE t.assigned_user = m.viejo;
ALTER TABLE task DROP COLUMN assigned_user;
ALTER TABLE task RENAME COLUMN assigned_user_uuid TO assigned_user;
ALTER TABLE task ALTER COLUMN assigned_user SET NOT NULL;

ALTER TABLE task ADD COLUMN created_by_uuid uuid;
UPDATE task t SET created_by_uuid = m.nuevo FROM _v3_users m WHERE t.created_by = m.viejo;
ALTER TABLE task DROP COLUMN created_by;
ALTER TABLE task RENAME COLUMN created_by_uuid TO created_by;
ALTER TABLE task ALTER COLUMN created_by SET NOT NULL;

-- answer_read
ALTER TABLE answer_read ADD COLUMN answer_id_uuid uuid;
UPDATE answer_read ar SET answer_id_uuid = m.nuevo FROM _v3_answer m WHERE ar.answer_id = m.viejo;
ALTER TABLE answer_read DROP COLUMN answer_id;
ALTER TABLE answer_read RENAME COLUMN answer_id_uuid TO answer_id;
ALTER TABLE answer_read ALTER COLUMN answer_id SET NOT NULL;

ALTER TABLE answer_read ADD COLUMN user_id_uuid uuid;
UPDATE answer_read ar SET user_id_uuid = m.nuevo FROM _v3_users m WHERE ar.user_id = m.viejo;
ALTER TABLE answer_read DROP COLUMN user_id;
ALTER TABLE answer_read RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE answer_read ALTER COLUMN user_id SET NOT NULL;

-- refresh_token
ALTER TABLE refresh_token ADD COLUMN user_id_uuid uuid;
UPDATE refresh_token rt SET user_id_uuid = m.nuevo FROM _v3_users m WHERE rt.user_id = m.viejo;
ALTER TABLE refresh_token DROP COLUMN user_id;
ALTER TABLE refresh_token RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE refresh_token ALTER COLUMN user_id SET NOT NULL;

-- audit_log (polimorficas: no hay FK, se reescriben por entidad conocida)
ALTER TABLE audit_log ADD COLUMN user_id_uuid uuid;
UPDATE audit_log a SET user_id_uuid = m.nuevo FROM _v3_users m WHERE a.user_id = m.viejo;
ALTER TABLE audit_log DROP COLUMN user_id;
ALTER TABLE audit_log RENAME COLUMN user_id_uuid TO user_id;

ALTER TABLE audit_log ADD COLUMN entity_id_uuid uuid;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_users m WHERE a.entity = 'User' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_users m WHERE a.entity = 'users' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_workflow m WHERE a.entity = 'Workflow' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_workflow_execution m WHERE a.entity = 'WorkflowExecution' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_ai_config m WHERE a.entity = 'AiConfig' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_routing_config m WHERE a.entity = 'RoutingConfig' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_microsoft_graph_config m WHERE a.entity = 'MicrosoftGraphConfig' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_report_config m WHERE a.entity = 'ReportConfig' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_mfa_setting m WHERE a.entity = 'MfaSetting' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_support_team m WHERE a.entity = 'teams' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_area m WHERE a.entity = 'areas' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_category m WHERE a.entity = 'categories' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_ticket_type m WHERE a.entity = 'ticket-types' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_sla_policy m WHERE a.entity = 'sla/policies' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_sla_rule m WHERE a.entity = 'sla/rules' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_sla_escalation m WHERE a.entity = 'sla/escalations' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_sucursal m WHERE a.entity = 'sucursales' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_notification_template m WHERE a.entity = 'templates' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
UPDATE audit_log a SET entity_id_uuid = m.nuevo FROM _v3_monthly_evaluation m WHERE a.entity = 'evaluations' AND a.entity_id = m.viejo AND a.entity_id_uuid IS NULL;
ALTER TABLE audit_log DROP COLUMN entity_id;
ALTER TABLE audit_log RENAME COLUMN entity_id_uuid TO entity_id;

-- ai_config / graph (sin FK: updated_by/changed_by son siempre un usuario)
ALTER TABLE ai_config ADD COLUMN updated_by_uuid uuid;
UPDATE ai_config ac SET updated_by_uuid = m.nuevo FROM _v3_users m WHERE ac.updated_by = m.viejo;
ALTER TABLE ai_config DROP COLUMN updated_by;
ALTER TABLE ai_config RENAME COLUMN updated_by_uuid TO updated_by;

ALTER TABLE microsoft_graph_config ADD COLUMN updated_by_uuid uuid;
UPDATE microsoft_graph_config mgc SET updated_by_uuid = m.nuevo FROM _v3_users m WHERE mgc.updated_by = m.viejo;
ALTER TABLE microsoft_graph_config DROP COLUMN updated_by;
ALTER TABLE microsoft_graph_config RENAME COLUMN updated_by_uuid TO updated_by;

ALTER TABLE graph_config_audit ADD COLUMN config_id_uuid uuid;
UPDATE graph_config_audit gca SET config_id_uuid = m.nuevo FROM _v3_microsoft_graph_config m WHERE gca.config_id = m.viejo;
ALTER TABLE graph_config_audit DROP COLUMN config_id;
ALTER TABLE graph_config_audit RENAME COLUMN config_id_uuid TO config_id;

ALTER TABLE graph_config_audit ADD COLUMN changed_by_uuid uuid;
UPDATE graph_config_audit gca SET changed_by_uuid = m.nuevo FROM _v3_users m WHERE gca.changed_by = m.viejo;
ALTER TABLE graph_config_audit DROP COLUMN changed_by;
ALTER TABLE graph_config_audit RENAME COLUMN changed_by_uuid TO changed_by;

-- notification_template_image
ALTER TABLE notification_template_image ADD COLUMN id_template_uuid uuid;
UPDATE notification_template_image nti SET id_template_uuid = m.nuevo FROM _v3_notification_template m WHERE nti.id_template = m.viejo;
ALTER TABLE notification_template_image DROP COLUMN id_template;
ALTER TABLE notification_template_image RENAME COLUMN id_template_uuid TO id_template;

-- password_reset_token / mfa
ALTER TABLE password_reset_token ADD COLUMN user_id_uuid uuid;
UPDATE password_reset_token prt SET user_id_uuid = m.nuevo FROM _v3_users m WHERE prt.user_id = m.viejo;
ALTER TABLE password_reset_token DROP COLUMN user_id;
ALTER TABLE password_reset_token RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE password_reset_token ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE user_mfa ADD COLUMN user_id_uuid uuid;
UPDATE user_mfa um SET user_id_uuid = m.nuevo FROM _v3_users m WHERE um.user_id = m.viejo;
ALTER TABLE user_mfa DROP COLUMN user_id;
ALTER TABLE user_mfa RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE user_mfa ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE mfa_challenge ADD COLUMN user_id_uuid uuid;
UPDATE mfa_challenge mc SET user_id_uuid = m.nuevo FROM _v3_users m WHERE mc.user_id = m.viejo;
ALTER TABLE mfa_challenge DROP COLUMN user_id;
ALTER TABLE mfa_challenge RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE mfa_challenge ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE mfa_recovery_code ADD COLUMN user_id_uuid uuid;
UPDATE mfa_recovery_code mrc SET user_id_uuid = m.nuevo FROM _v3_users m WHERE mrc.user_id = m.viejo;
ALTER TABLE mfa_recovery_code DROP COLUMN user_id;
ALTER TABLE mfa_recovery_code RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE mfa_recovery_code ALTER COLUMN user_id SET NOT NULL;

-- singletons: quien edito cada configuracion
ALTER TABLE session_config ADD COLUMN updated_by_uuid uuid;
UPDATE session_config sc SET updated_by_uuid = m.nuevo FROM _v3_users m WHERE sc.updated_by = m.viejo;
ALTER TABLE session_config DROP COLUMN updated_by;
ALTER TABLE session_config RENAME COLUMN updated_by_uuid TO updated_by;

ALTER TABLE chat_config ADD COLUMN updated_by_uuid uuid;
UPDATE chat_config cc SET updated_by_uuid = m.nuevo FROM _v3_users m WHERE cc.updated_by = m.viejo;
ALTER TABLE chat_config DROP COLUMN updated_by;
ALTER TABLE chat_config RENAME COLUMN updated_by_uuid TO updated_by;

ALTER TABLE error_page_config ADD COLUMN updated_by_uuid uuid;
UPDATE error_page_config epc SET updated_by_uuid = m.nuevo FROM _v3_users m WHERE epc.updated_by = m.viejo;
ALTER TABLE error_page_config DROP COLUMN updated_by;
ALTER TABLE error_page_config RENAME COLUMN updated_by_uuid TO updated_by;

ALTER TABLE privacy_config ADD COLUMN updated_by_uuid uuid;
UPDATE privacy_config pc SET updated_by_uuid = m.nuevo FROM _v3_users m WHERE pc.updated_by = m.viejo;
ALTER TABLE privacy_config DROP COLUMN updated_by;
ALTER TABLE privacy_config RENAME COLUMN updated_by_uuid TO updated_by;

ALTER TABLE mfa_setting ADD COLUMN updated_by_mfa_uuid uuid;
UPDATE mfa_setting ms SET updated_by_mfa_uuid = m.nuevo FROM _v3_users m WHERE ms.updated_by_mfa = m.viejo;
ALTER TABLE mfa_setting DROP COLUMN updated_by_mfa;
ALTER TABLE mfa_setting RENAME COLUMN updated_by_mfa_uuid TO updated_by_mfa;

-- workflow (sin FK en updated_by, pero siempre un usuario)
ALTER TABLE workflow ADD COLUMN created_by_workflow_uuid uuid;
UPDATE workflow w SET created_by_workflow_uuid = m.nuevo FROM _v3_users m WHERE w.created_by_workflow = m.viejo;
ALTER TABLE workflow DROP COLUMN created_by_workflow;
ALTER TABLE workflow RENAME COLUMN created_by_workflow_uuid TO created_by_workflow;
ALTER TABLE workflow ALTER COLUMN created_by_workflow SET NOT NULL;

ALTER TABLE workflow ADD COLUMN updated_by_workflow_uuid uuid;
UPDATE workflow w SET updated_by_workflow_uuid = m.nuevo FROM _v3_users m WHERE w.updated_by_workflow = m.viejo;
ALTER TABLE workflow DROP COLUMN updated_by_workflow;
ALTER TABLE workflow RENAME COLUMN updated_by_workflow_uuid TO updated_by_workflow;

-- workflow_version
ALTER TABLE workflow_version ADD COLUMN workflow_workflow_uuid uuid;
UPDATE workflow_version wv SET workflow_workflow_uuid = m.nuevo FROM _v3_workflow m WHERE wv.workflow_workflow = m.viejo;
ALTER TABLE workflow_version DROP COLUMN workflow_workflow;
ALTER TABLE workflow_version RENAME COLUMN workflow_workflow_uuid TO workflow_workflow;
ALTER TABLE workflow_version ALTER COLUMN workflow_workflow SET NOT NULL;

ALTER TABLE workflow_version ADD COLUMN created_by_workflow_uuid uuid;
UPDATE workflow_version wv SET created_by_workflow_uuid = m.nuevo FROM _v3_users m WHERE wv.created_by_workflow = m.viejo;
ALTER TABLE workflow_version DROP COLUMN created_by_workflow;
ALTER TABLE workflow_version RENAME COLUMN created_by_workflow_uuid TO created_by_workflow;
ALTER TABLE workflow_version ALTER COLUMN created_by_workflow SET NOT NULL;

-- workflow_execution
ALTER TABLE workflow_execution ADD COLUMN workflow_workflow_uuid uuid;
UPDATE workflow_execution we SET workflow_workflow_uuid = m.nuevo FROM _v3_workflow m WHERE we.workflow_workflow = m.viejo;
ALTER TABLE workflow_execution DROP COLUMN workflow_workflow;
ALTER TABLE workflow_execution RENAME COLUMN workflow_workflow_uuid TO workflow_workflow;
ALTER TABLE workflow_execution ALTER COLUMN workflow_workflow SET NOT NULL;

ALTER TABLE workflow_execution ADD COLUMN version_workflow_uuid uuid;
UPDATE workflow_execution we SET version_workflow_uuid = m.nuevo FROM _v3_workflow_version m WHERE we.version_workflow = m.viejo;
ALTER TABLE workflow_execution DROP COLUMN version_workflow;
ALTER TABLE workflow_execution RENAME COLUMN version_workflow_uuid TO version_workflow;
ALTER TABLE workflow_execution ALTER COLUMN version_workflow SET NOT NULL;

ALTER TABLE workflow_execution ADD COLUMN started_by_execution_uuid uuid;
UPDATE workflow_execution we SET started_by_execution_uuid = m.nuevo FROM _v3_users m WHERE we.started_by_execution = m.viejo;
ALTER TABLE workflow_execution DROP COLUMN started_by_execution;
ALTER TABLE workflow_execution RENAME COLUMN started_by_execution_uuid TO started_by_execution;

ALTER TABLE workflow_execution ADD COLUMN entity_id_execution_uuid uuid;
UPDATE workflow_execution we SET entity_id_execution_uuid = m.nuevo FROM _v3_ticket m
    WHERE we.entity_type_execution = 'TICKET' AND we.entity_id_execution = m.viejo AND we.entity_id_execution_uuid IS NULL;
UPDATE workflow_execution we SET entity_id_execution_uuid = m.nuevo FROM _v3_users m
    WHERE we.entity_type_execution = 'USER' AND we.entity_id_execution = m.viejo AND we.entity_id_execution_uuid IS NULL;
ALTER TABLE workflow_execution DROP COLUMN entity_id_execution;
ALTER TABLE workflow_execution RENAME COLUMN entity_id_execution_uuid TO entity_id_execution;

ALTER TABLE workflow_execution ADD COLUMN origin_execution_uuid uuid;
UPDATE workflow_execution we SET origin_execution_uuid = m.nuevo FROM _v3_workflow_execution m WHERE we.origin_execution = m.viejo;
ALTER TABLE workflow_execution DROP COLUMN origin_execution;
ALTER TABLE workflow_execution RENAME COLUMN origin_execution_uuid TO origin_execution;

-- workflow_node_run
ALTER TABLE workflow_node_run ADD COLUMN execution_execution_uuid uuid;
UPDATE workflow_node_run wnr SET execution_execution_uuid = m.nuevo FROM _v3_workflow_execution m WHERE wnr.execution_execution = m.viejo;
ALTER TABLE workflow_node_run DROP COLUMN execution_execution;
ALTER TABLE workflow_node_run RENAME COLUMN execution_execution_uuid TO execution_execution;
ALTER TABLE workflow_node_run ALTER COLUMN execution_execution SET NOT NULL;

-- workflow_wait
ALTER TABLE workflow_wait ADD COLUMN execution_execution_uuid uuid;
UPDATE workflow_wait ww SET execution_execution_uuid = m.nuevo FROM _v3_workflow_execution m WHERE ww.execution_execution = m.viejo;
ALTER TABLE workflow_wait DROP COLUMN execution_execution;
ALTER TABLE workflow_wait RENAME COLUMN execution_execution_uuid TO execution_execution;
ALTER TABLE workflow_wait ALTER COLUMN execution_execution SET NOT NULL;

ALTER TABLE workflow_wait ADD COLUMN approver_user_wait_uuid uuid;
UPDATE workflow_wait ww SET approver_user_wait_uuid = m.nuevo FROM _v3_users m WHERE ww.approver_user_wait = m.viejo;
ALTER TABLE workflow_wait DROP COLUMN approver_user_wait;
ALTER TABLE workflow_wait RENAME COLUMN approver_user_wait_uuid TO approver_user_wait;

ALTER TABLE workflow_wait ADD COLUMN decided_by_wait_uuid uuid;
UPDATE workflow_wait ww SET decided_by_wait_uuid = m.nuevo FROM _v3_users m WHERE ww.decided_by_wait = m.viejo;
ALTER TABLE workflow_wait DROP COLUMN decided_by_wait;
ALTER TABLE workflow_wait RENAME COLUMN decided_by_wait_uuid TO decided_by_wait;

-- workflow_event_dedup
ALTER TABLE workflow_event_dedup ADD COLUMN workflow_workflow_uuid uuid;
UPDATE workflow_event_dedup wed SET workflow_workflow_uuid = m.nuevo FROM _v3_workflow m WHERE wed.workflow_workflow = m.viejo;
ALTER TABLE workflow_event_dedup DROP COLUMN workflow_workflow;
ALTER TABLE workflow_event_dedup RENAME COLUMN workflow_workflow_uuid TO workflow_workflow;
ALTER TABLE workflow_event_dedup ALTER COLUMN workflow_workflow SET NOT NULL;

ALTER TABLE workflow_event_dedup ADD COLUMN execution_execution_uuid uuid;
UPDATE workflow_event_dedup wed SET execution_execution_uuid = m.nuevo FROM _v3_workflow_execution m WHERE wed.execution_execution = m.viejo;
ALTER TABLE workflow_event_dedup DROP COLUMN execution_execution;
ALTER TABLE workflow_event_dedup RENAME COLUMN execution_execution_uuid TO execution_execution;
ALTER TABLE workflow_event_dedup ALTER COLUMN execution_execution SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 5) RESTAURACION
-- ----------------------------------------------------------------------------
-- 5.1 Constraints UNIQUE que vivian sobre columnas convertidas
ALTER TABLE team_user ADD CONSTRAINT uq_team_user UNIQUE (team_id, user_id);
ALTER TABLE sla_rule ADD CONSTRAINT uq_sla_rule UNIQUE (area_id, priority_id, category_id);
ALTER TABLE ticket_sla ADD CONSTRAINT uq_ticket_sla_ticket UNIQUE (ticket_id);
ALTER TABLE answer_read ADD CONSTRAINT uq_answer_read UNIQUE (answer_id, user_id);
ALTER TABLE user_mfa ADD CONSTRAINT uq_user_mfa_user UNIQUE (user_id);
ALTER TABLE sla_escalation ADD CONSTRAINT uq_escalation_policy_pct UNIQUE (sla_policy_id, trigger_percentage);
ALTER TABLE workflow_version ADD CONSTRAINT uq_workflow_version_num UNIQUE (workflow_workflow, version_workflow);
ALTER TABLE workflow_event_dedup ADD CONSTRAINT uq_workflow_event_dedup UNIQUE (workflow_workflow, correlation_key);

-- 5.2 Indices que vivian sobre columnas convertidas
CREATE INDEX IF NOT EXISTS ix_users_sucursal   ON users (sucursal_id);
CREATE INDEX IF NOT EXISTS ix_ticket_sucursal  ON ticket (sucursal_id);
CREATE INDEX IF NOT EXISTS idx_answer_read_answer ON answer_read (answer_id);
CREATE INDEX IF NOT EXISTS idx_ticket_user     ON ticket (user_ticket);
CREATE INDEX IF NOT EXISTS idx_ticket_agent    ON ticket (agent_ticket);
CREATE INDEX IF NOT EXISTS idx_ticket_status   ON ticket (status_ticket);
CREATE INDEX IF NOT EXISTS idx_rating_ticket   ON ticket_rating (ticket_id);
CREATE INDEX IF NOT EXISTS idx_rating_user     ON ticket_rating (user_id);
CREATE INDEX IF NOT EXISTS idx_eval_agent      ON monthly_evaluation (agent_id);
CREATE INDEX IF NOT EXISTS idx_audit_ticket    ON ticket_audit (ticket_id);
CREATE INDEX IF NOT EXISTS idx_notif_user_created
    ON notification (user_id, created_at_notification DESC);
CREATE INDEX IF NOT EXISTS idx_refresh_user    ON refresh_token (user_id);
CREATE INDEX IF NOT EXISTS idx_task_assigned   ON task (assigned_user);
CREATE INDEX IF NOT EXISTS idx_pw_reset_user   ON password_reset_token (user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_user ON mfa_recovery_code (user_id);
CREATE INDEX IF NOT EXISTS ix_template_image_template ON notification_template_image (id_template);
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_version_activa
    ON workflow_version (workflow_workflow) WHERE is_active_version = TRUE;
CREATE INDEX IF NOT EXISTS idx_wf_version_workflow ON workflow_version (workflow_workflow);
CREATE INDEX IF NOT EXISTS idx_wf_exec_workflow
    ON workflow_execution (workflow_workflow, created_at_execution DESC);
CREATE INDEX IF NOT EXISTS idx_node_run_exec
    ON workflow_node_run (execution_execution, sequence_node_run);
CREATE INDEX IF NOT EXISTS idx_wf_exec_entidad
    ON workflow_execution (entity_type_execution, entity_id_execution);

-- 5.3 Claves foraneas (mismos nombres y reglas que en V1)
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (rol_user) REFERENCES role (id_role);
ALTER TABLE users ADD CONSTRAINT fk_users_area FOREIGN KEY (area_id) REFERENCES area (id_area);
ALTER TABLE users ADD CONSTRAINT fk_users_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal (id_sucursal) ON DELETE SET NULL;
ALTER TABLE team_user ADD CONSTRAINT fk_tu_team FOREIGN KEY (team_id) REFERENCES support_team (id_team);
ALTER TABLE team_user ADD CONSTRAINT fk_tu_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE category ADD CONSTRAINT fk_category_team FOREIGN KEY (team_category) REFERENCES support_team (id_team);
ALTER TABLE sla_rule ADD CONSTRAINT fk_slarule_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policy (id_sla_policy);
ALTER TABLE sla_rule ADD CONSTRAINT fk_slarule_area FOREIGN KEY (area_id) REFERENCES area (id_area);
ALTER TABLE sla_rule ADD CONSTRAINT fk_slarule_priority FOREIGN KEY (priority_id) REFERENCES priority (id_priority);
ALTER TABLE sla_rule ADD CONSTRAINT fk_slarule_category FOREIGN KEY (category_id) REFERENCES category (id_category);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_user FOREIGN KEY (user_ticket) REFERENCES users (id_user);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_agent FOREIGN KEY (agent_ticket) REFERENCES users (id_user);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_team FOREIGN KEY (team_ticket) REFERENCES support_team (id_team);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_area FOREIGN KEY (area_ticket) REFERENCES area (id_area);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_category FOREIGN KEY (category_ticket) REFERENCES category (id_category);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_status FOREIGN KEY (status_ticket) REFERENCES status (id_status);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_priority FOREIGN KEY (priority_ticket) REFERENCES priority (id_priority);
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal (id_sucursal) ON DELETE SET NULL;
ALTER TABLE ticket_sla ADD CONSTRAINT fk_ts_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket);
ALTER TABLE ticket_sla ADD CONSTRAINT fk_ts_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policy (id_sla_policy);
ALTER TABLE answer ADD CONSTRAINT fk_answer_ticket FOREIGN KEY (ticket_answer) REFERENCES ticket (id_ticket);
ALTER TABLE answer ADD CONSTRAINT fk_answer_user FOREIGN KEY (user_answer) REFERENCES users (id_user);
ALTER TABLE attach ADD CONSTRAINT fk_attach_ticket FOREIGN KEY (ticket_attach) REFERENCES ticket (id_ticket);
ALTER TABLE attach ADD CONSTRAINT fk_attach_answer FOREIGN KEY (answer_attach) REFERENCES answer (id_answer);
ALTER TABLE ticket_audit ADD CONSTRAINT fk_audit_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket);
ALTER TABLE ticket_audit ADD CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE notification ADD CONSTRAINT fk_notif_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket);
ALTER TABLE notification ADD CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE sla_escalation ADD CONSTRAINT fk_esc_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policy (id_sla_policy);
ALTER TABLE sla_escalation ADD CONSTRAINT fk_esc_team FOREIGN KEY (destination_team_id) REFERENCES support_team (id_team);
ALTER TABLE sla_escalation ADD CONSTRAINT fk_esc_user FOREIGN KEY (destination_user_id) REFERENCES users (id_user);
ALTER TABLE sla_escalation ADD CONSTRAINT fk_esc_template FOREIGN KEY (notification_template_id) REFERENCES notification_template (id_template);
ALTER TABLE ticket_rating ADD CONSTRAINT fk_rating_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket);
ALTER TABLE ticket_rating ADD CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE monthly_evaluation ADD CONSTRAINT fk_eval_agent FOREIGN KEY (agent_id) REFERENCES users (id_user);
ALTER TABLE monthly_evaluation ADD CONSTRAINT fk_eval_by FOREIGN KEY (evaluated_by) REFERENCES users (id_user);
ALTER TABLE task ADD CONSTRAINT fk_task_assigned FOREIGN KEY (assigned_user) REFERENCES users (id_user);
ALTER TABLE task ADD CONSTRAINT fk_task_created FOREIGN KEY (created_by) REFERENCES users (id_user);
ALTER TABLE answer_read ADD CONSTRAINT fk_answer_read_answer FOREIGN KEY (answer_id) REFERENCES answer (id_answer) ON DELETE CASCADE;
ALTER TABLE answer_read ADD CONSTRAINT fk_answer_read_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE refresh_token ADD CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE notification_template_image ADD CONSTRAINT fk_template_image_template FOREIGN KEY (id_template) REFERENCES notification_template (id_template) ON DELETE CASCADE;
ALTER TABLE password_reset_token ADD CONSTRAINT fk_pw_reset_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE user_mfa ADD CONSTRAINT fk_user_mfa_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE mfa_challenge ADD CONSTRAINT fk_mfa_challenge_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE mfa_recovery_code ADD CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES users (id_user);
ALTER TABLE workflow ADD CONSTRAINT fk_workflow_creado_por FOREIGN KEY (created_by_workflow) REFERENCES users (id_user);
ALTER TABLE workflow_version ADD CONSTRAINT fk_wf_version_wf FOREIGN KEY (workflow_workflow) REFERENCES workflow (id_workflow);
ALTER TABLE workflow_version ADD CONSTRAINT fk_wf_version_creado_por FOREIGN KEY (created_by_workflow) REFERENCES users (id_user);
ALTER TABLE workflow_execution ADD CONSTRAINT fk_wf_exec_wf FOREIGN KEY (workflow_workflow) REFERENCES workflow (id_workflow);
ALTER TABLE workflow_execution ADD CONSTRAINT fk_wf_exec_version FOREIGN KEY (version_workflow) REFERENCES workflow_version (id_workflow_version);
ALTER TABLE workflow_execution ADD CONSTRAINT fk_wf_exec_iniciado_por FOREIGN KEY (started_by_execution) REFERENCES users (id_user);
ALTER TABLE workflow_node_run ADD CONSTRAINT fk_node_run_exec FOREIGN KEY (execution_execution) REFERENCES workflow_execution (id_workflow_execution);
ALTER TABLE workflow_wait ADD CONSTRAINT fk_workflow_wait_exec FOREIGN KEY (execution_execution) REFERENCES workflow_execution (id_workflow_execution);
ALTER TABLE workflow_wait ADD CONSTRAINT fk_workflow_wait_approver_user FOREIGN KEY (approver_user_wait) REFERENCES users (id_user);
ALTER TABLE workflow_wait ADD CONSTRAINT fk_workflow_wait_decided_by FOREIGN KEY (decided_by_wait) REFERENCES users (id_user);
ALTER TABLE workflow_event_dedup ADD CONSTRAINT fk_event_dedup_wf FOREIGN KEY (workflow_workflow) REFERENCES workflow (id_workflow);
ALTER TABLE workflow_event_dedup ADD CONSTRAINT fk_event_dedup_exec FOREIGN KEY (execution_execution) REFERENCES workflow_execution (id_workflow_execution);

-- 5.4 Check de fila unica de las tablas singleton, ahora sobre el uuid fijo
ALTER TABLE session_config ADD CONSTRAINT uq_session_config_singleton
    CHECK (id_session_config = '00000000-0000-0000-0000-000000000001');
ALTER TABLE routing_config ADD CONSTRAINT uq_routing_config_singleton
    CHECK (id_routing_config = '00000000-0000-0000-0000-000000000002');
ALTER TABLE chat_config ADD CONSTRAINT uq_chat_config_singleton
    CHECK (id_chat_config = '00000000-0000-0000-0000-000000000003');
ALTER TABLE error_page_config ADD CONSTRAINT uq_error_page_config_singleton
    CHECK (id_error_page = '00000000-0000-0000-0000-000000000004');
ALTER TABLE privacy_config ADD CONSTRAINT uq_privacy_config_singleton
    CHECK (id_privacy_config = '00000000-0000-0000-0000-000000000005');
ALTER TABLE mfa_setting ADD CONSTRAINT uq_mfa_setting_singleton
    CHECK (id_mfa_setting = '00000000-0000-0000-0000-000000000006');

-- 5.5 Triggers de solo insercion (se retiraron al principio)
CREATE TRIGGER trg_audit_log_inmutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION auditoria_solo_insercion();
CREATE TRIGGER trg_audit_log_sin_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_solo_insercion();
CREATE TRIGGER trg_ticket_audit_inmutable BEFORE UPDATE OR DELETE ON ticket_audit
    FOR EACH ROW EXECUTE FUNCTION auditoria_solo_insercion();
CREATE TRIGGER trg_ticket_audit_sin_truncate BEFORE TRUNCATE ON ticket_audit
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_solo_insercion();