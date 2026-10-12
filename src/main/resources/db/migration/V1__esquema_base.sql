-- ============================================================
-- SISTEMA DE MESA DE AYUDA / HELP DESK
-- 20 TABLAS - PostgreSQL 16
-- Script idempotente: CREATE TABLE IF NOT EXISTS
-- ============================================================

-- 1. ROLE
CREATE TABLE IF NOT EXISTS role (
    id_role           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_role         VARCHAR(100) NOT NULL UNIQUE,
    description_role  VARCHAR(255),
    created_at_role   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_role   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_role      BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2. AREA
CREATE TABLE IF NOT EXISTS area (
    id_area           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_area         VARCHAR(100) NOT NULL UNIQUE,
    description_area  VARCHAR(255),
    priority_level    INTEGER NOT NULL DEFAULT 1,
    status_area       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_area   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_area   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_area      BOOLEAN NOT NULL DEFAULT FALSE
);

-- 3. USERS
CREATE TABLE IF NOT EXISTS users (
    id_user                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identification_user      VARCHAR(30) NOT NULL UNIQUE,
    corporate_email_user     VARCHAR(150) NOT NULL UNIQUE,
    password_user            VARCHAR(255) NOT NULL,
    phone_user               VARCHAR(30),
    status_user              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    rol_user                 BIGINT NOT NULL,
    area_id                  BIGINT NOT NULL,
    must_change_password     BOOLEAN NOT NULL DEFAULT FALSE,
    temporal_password_expires_at TIMESTAMPTZ,
    created_at_user          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_user          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_user             BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_users_role  FOREIGN KEY (rol_user)  REFERENCES role (id_role),
    CONSTRAINT fk_users_area  FOREIGN KEY (area_id)   REFERENCES area (id_area)
);

-- 4. STATUS
CREATE TABLE IF NOT EXISTS status (
    id_status         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_status       VARCHAR(100) NOT NULL UNIQUE,
    description_status VARCHAR(255),
    icon_status       VARCHAR(100),
    color_status      VARCHAR(30),
    created_at_status TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_status TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_status    BOOLEAN NOT NULL DEFAULT FALSE
);

-- 5. PRIORITY
CREATE TABLE IF NOT EXISTS priority (
    id_priority         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_priority       VARCHAR(100) NOT NULL UNIQUE,
    level_priority      INTEGER NOT NULL UNIQUE,
    description_priority VARCHAR(255),
    icon_priority       VARCHAR(100),
    color_priority      VARCHAR(30),
    created_at_priority TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_priority TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_priority    BOOLEAN NOT NULL DEFAULT FALSE
);

-- 6. SUPPORT_TEAM
CREATE TABLE IF NOT EXISTS support_team (
    id_team            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_team          VARCHAR(100) NOT NULL UNIQUE,
    description_team   VARCHAR(255),
    assignment_method  VARCHAR(30) NOT NULL DEFAULT 'ROUND_ROBIN',
    active_team        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_team    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_team    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. TEAM_USER
CREATE TABLE IF NOT EXISTS team_user (
    id_team_user      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id           BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    active_team_user  BOOLEAN NOT NULL DEFAULT TRUE,
    last_assigned_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tu_team FOREIGN KEY (team_id) REFERENCES support_team (id_team),
    CONSTRAINT fk_tu_user FOREIGN KEY (user_id) REFERENCES users (id_user),
    CONSTRAINT uq_team_user UNIQUE (team_id, user_id)
);

-- 8. CATEGORY
CREATE TABLE IF NOT EXISTS category (
    id_category         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_category       VARCHAR(100) NOT NULL UNIQUE,
    description_category VARCHAR(255),
    icon_category       VARCHAR(100),
    color_category      VARCHAR(30),
    team_category       BIGINT,
    created_at_category TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_category TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_category    BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_category_team FOREIGN KEY (team_category) REFERENCES support_team (id_team)
);

-- 9. SLA_POLICY
CREATE TABLE IF NOT EXISTS sla_policy (
    id_sla_policy         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_sla_policy       VARCHAR(100) NOT NULL UNIQUE,
    description_sla_policy VARCHAR(255),
    response_time_minutes INTEGER NOT NULL,
    resolution_time_minutes INTEGER NOT NULL,
    active_sla_policy     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_sla_policy TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_sla_policy TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_response_gt0 CHECK (response_time_minutes > 0),
    CONSTRAINT chk_resolution_gt0 CHECK (resolution_time_minutes > 0)
);

-- 10. SLA_RULE
CREATE TABLE IF NOT EXISTS sla_rule (
    id_sla_rule        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sla_policy_id      BIGINT NOT NULL,
    area_id            BIGINT NOT NULL,
    priority_id        BIGINT NOT NULL,
    category_id        BIGINT,
    active_sla_rule    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_sla_rule TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_sla_rule TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_slarule_policy    FOREIGN KEY (sla_policy_id) REFERENCES sla_policy (id_sla_policy),
    CONSTRAINT fk_slarule_area      FOREIGN KEY (area_id)       REFERENCES area (id_area),
    CONSTRAINT fk_slarule_priority  FOREIGN KEY (priority_id)   REFERENCES priority (id_priority),
    CONSTRAINT fk_slarule_category  FOREIGN KEY (category_id)   REFERENCES category (id_category),
    CONSTRAINT uq_sla_rule          UNIQUE (area_id, priority_id, category_id)
);

-- 11. TICKET
CREATE TABLE IF NOT EXISTS ticket (
    id_ticket            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title_ticket         VARCHAR(255) NOT NULL,
    description_ticket   TEXT,
    user_ticket          BIGINT NOT NULL,
    agent_ticket         BIGINT,
    team_ticket          BIGINT,
    area_ticket          BIGINT NOT NULL,
    category_ticket      BIGINT NOT NULL,
    status_ticket        BIGINT NOT NULL,
    priority_ticket      BIGINT NOT NULL,
    closing_date_ticket  TIMESTAMPTZ,
    created_at_ticket    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_ticket    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_ticket       BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_ticket_user        FOREIGN KEY (user_ticket)      REFERENCES users (id_user),
    CONSTRAINT fk_ticket_agent       FOREIGN KEY (agent_ticket)     REFERENCES users (id_user),
    CONSTRAINT fk_ticket_team        FOREIGN KEY (team_ticket)      REFERENCES support_team (id_team),
    CONSTRAINT fk_ticket_area        FOREIGN KEY (area_ticket)      REFERENCES area (id_area),
    CONSTRAINT fk_ticket_category    FOREIGN KEY (category_ticket)  REFERENCES category (id_category),
    CONSTRAINT fk_ticket_status      FOREIGN KEY (status_ticket)    REFERENCES status (id_status),
    CONSTRAINT fk_ticket_priority    FOREIGN KEY (priority_ticket)  REFERENCES priority (id_priority)
);

-- 12. TICKET_SLA
CREATE TABLE IF NOT EXISTS ticket_sla (
    id_ticket_sla            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id                BIGINT NOT NULL UNIQUE,
    sla_policy_id            BIGINT NOT NULL,
    response_due_at          TIMESTAMPTZ,
    resolution_due_at        TIMESTAMPTZ,
    response_completed_at    TIMESTAMPTZ,
    resolution_completed_at  TIMESTAMPTZ,
    response_breached        BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_breached      BOOLEAN NOT NULL DEFAULT FALSE,
    paused_at                TIMESTAMPTZ,
    total_paused_minutes     INTEGER NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ts_ticket FOREIGN KEY (ticket_id)     REFERENCES ticket (id_ticket),
    CONSTRAINT fk_ts_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policy (id_sla_policy)
);

-- 13. ANSWER
CREATE TABLE IF NOT EXISTS answer (
    id_answer         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_answer    TEXT NOT NULL,
    internal_answer   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at_answer TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_answer TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_answer    BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_answer     BIGINT NOT NULL,
    user_answer       BIGINT NOT NULL,
    CONSTRAINT fk_answer_ticket FOREIGN KEY (ticket_answer) REFERENCES ticket (id_ticket),
    CONSTRAINT fk_answer_user   FOREIGN KEY (user_answer)   REFERENCES users (id_user)
);

-- 14. ATTACH
CREATE TABLE IF NOT EXISTS attach (
    id_attach            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name_attach     VARCHAR(255) NOT NULL,
    storage_path_attach  TEXT NOT NULL,
    size_file_attach     BIGINT,
    mime_type_attach     VARCHAR(100),
    created_at_attach    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_attach       BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_attach        BIGINT NOT NULL,
    answer_attach        BIGINT,
    CONSTRAINT fk_attach_ticket FOREIGN KEY (ticket_attach) REFERENCES ticket (id_ticket),
    CONSTRAINT fk_attach_answer FOREIGN KEY (answer_attach) REFERENCES answer (id_answer)
);

-- 15. TICKET_AUDIT
CREATE TABLE IF NOT EXISTS ticket_audit (
    id_audit          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id         BIGINT NOT NULL,
    user_id           BIGINT,
    action_type       VARCHAR(50) NOT NULL,
    old_value         TEXT,
    new_value         TEXT,
    metadata          TEXT,
    created_at_audit  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket),
    CONSTRAINT fk_audit_user   FOREIGN KEY (user_id)   REFERENCES users (id_user)
);

-- 16. NOTIFICATION_TEMPLATE
CREATE TABLE IF NOT EXISTS notification_template (
    id_template        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_template      VARCHAR(100) NOT NULL UNIQUE,
    type_template      VARCHAR(50) NOT NULL,
    subject_template   VARCHAR(255) NOT NULL,
    body_template      TEXT NOT NULL,
    active_template    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_template TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_template TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 17. NOTIFICATION
CREATE TABLE IF NOT EXISTS notification (
    id_notification       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id             BIGINT,
    user_id               BIGINT,
    type_notification     VARCHAR(50) NOT NULL,
    subject_notification  VARCHAR(255) NOT NULL,
    message_notification  TEXT NOT NULL,
    status_notification   VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    read_notification     BOOLEAN NOT NULL DEFAULT FALSE,
    read_at_notification  TIMESTAMPTZ,
    sent_at               TIMESTAMPTZ,
    created_at_notification TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket),
    CONSTRAINT fk_notif_user   FOREIGN KEY (user_id)   REFERENCES users (id_user)
);

-- 18. SLA_ESCALATION
CREATE TABLE IF NOT EXISTS sla_escalation (
    id_escalation          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sla_policy_id          BIGINT NOT NULL,
    trigger_percentage     INTEGER NOT NULL,
    action_type            VARCHAR(50) NOT NULL,
    destination_team_id    BIGINT,
    destination_user_id    BIGINT,
    notification_template_id BIGINT,
    created_at_escalation  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_esc_policy   FOREIGN KEY (sla_policy_id)          REFERENCES sla_policy (id_sla_policy),
    CONSTRAINT fk_esc_team     FOREIGN KEY (destination_team_id)     REFERENCES support_team (id_team),
    CONSTRAINT fk_esc_user     FOREIGN KEY (destination_user_id)     REFERENCES users (id_user),
    CONSTRAINT fk_esc_template FOREIGN KEY (notification_template_id) REFERENCES notification_template (id_template),
    CONSTRAINT uq_escalation_policy_pct UNIQUE (sla_policy_id, trigger_percentage),
    CONSTRAINT chk_escalation_pct CHECK (trigger_percentage BETWEEN 1 AND 100)
);

-- 19. TICKET_RATING
CREATE TABLE IF NOT EXISTS ticket_rating (
    id_rating              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id              BIGINT NOT NULL,
    user_id                BIGINT NOT NULL,
    requirements_met       INTEGER NOT NULL,
    all_requirements_met   BOOLEAN NOT NULL DEFAULT FALSE,
    response_time_rating   INTEGER,
    resolution_quality     INTEGER,
    agent_communication    INTEGER,
    overall_satisfaction   INTEGER,
    comments               TEXT,
    rated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    evaluation_month       DATE NOT NULL,
    CONSTRAINT fk_rating_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id_ticket),
    CONSTRAINT fk_rating_user   FOREIGN KEY (user_id)   REFERENCES users (id_user),
    CONSTRAINT chk_req_met CHECK (requirements_met BETWEEN 1 AND 5)
);

-- 20. MONTHLY_EVALUATION
CREATE TABLE IF NOT EXISTS monthly_evaluation (
    id_evaluation              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id                   BIGINT NOT NULL,
    evaluation_month           DATE NOT NULL,
    tickets_closed             INTEGER NOT NULL DEFAULT 0,
    avg_response_time_minutes  DECIMAL(10,2),
    avg_resolution_time_minutes DECIMAL(10,2),
    sla_compliance_rate        DECIMAL(5,2),
    avg_requirements_met       DECIMAL(3,2),
    avg_response_time_rating   DECIMAL(3,2),
    avg_resolution_quality     DECIMAL(3,2),
    avg_agent_communication    DECIMAL(3,2),
    avg_overall_satisfaction   DECIMAL(3,2),
    requirements_met_count     INTEGER DEFAULT 0,
    satisfaction_score         DECIMAL(3,2),
    supervisor_rating          INTEGER,
    supervisor_comments        TEXT,
    evaluated_by               BIGINT,
    status                     VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_agent  FOREIGN KEY (agent_id)    REFERENCES users (id_user),
    CONSTRAINT fk_eval_by     FOREIGN KEY (evaluated_by) REFERENCES users (id_user)
);

-- 21. BRANDING_CONFIG
CREATE TABLE IF NOT EXISTS branding_config (
    id_branding      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    logo_url         VARCHAR(500),
    background_url   VARCHAR(500),
    primary_color    VARCHAR(7) NOT NULL DEFAULT '#062e86',
    secondary_color  VARCHAR(7) NOT NULL DEFAULT '#ec9811',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Columna background_url en branding_config (para BD existentes)
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS background_url VARCHAR(500);

-- Modo mantenimiento: lo activa un administrador cuando un sistema de la
-- institucion no funciona, sin tocar codigo.
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_title VARCHAR(160);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_message VARCHAR(1000);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_eta VARCHAR(160);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_logo_url VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_icon VARCHAR(120);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS maintenance_icon_color VARCHAR(7);

-- Pantalla de carga (splash) que se muestra al arrancar la aplicacion.
-- El color de fondo por defecto es el color primario de la marca (#062e86);
-- si no hay imagen ni icono propios, se usa el icono del sistema.
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_mode             VARCHAR(20) NOT NULL DEFAULT 'AUTO';
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_image_url         VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_icon              VARCHAR(120);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_background_color  VARCHAR(7);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_text_color        VARCHAR(7);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_title             VARCHAR(160);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_message           VARCHAR(160);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_animation          VARCHAR(20) NOT NULL DEFAULT 'SPINNER';
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_logo_effect        VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_progress          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_theme             VARCHAR(10) NOT NULL DEFAULT 'LIGHT';
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_min_duration_ms   INTEGER NOT NULL DEFAULT 1200;

-- "Solo el logo": cuando el administrador sube su propia imagen o elige un
-- icono, el splash muestra unicamente esa marca, sin textos ni indicador de
-- carga encima. Es un unico interruptor porque titulo y mensaje siempre van
-- juntos en esta pantalla.
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_solo_marca        BOOLEAN NOT NULL DEFAULT FALSE;
-- Lado del logo del splash, en px. 112 es el tamaño que venia usando el
-- splash fijo; se acota a 48..256 para que ni una imagen diminuta quede
-- perdida ni una enorme se salga de la pantalla en un movil.
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS loading_image_size       INTEGER NOT NULL DEFAULT 112;

-- 24. SUCURSAL
-- Sede fisica de la institucion. Una persona pertenece a una y cada ticket se
-- levanta para una, que son dos cosas distintas: alguien de la sede de Quito
-- puede abrir un ticket que corresponde a la de Guayaquil.
CREATE TABLE IF NOT EXISTS sucursal (
    id_sucursal      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_sucursal    VARCHAR(20) NOT NULL UNIQUE,
    name_sucursal    VARCHAR(100) NOT NULL,
    description_sucursal VARCHAR(255),
    address_sucursal VARCHAR(255),
    phone_sucursal   VARCHAR(40),
    icon_sucursal    VARCHAR(100),
    color_sucursal   VARCHAR(30),
    status_sucursal  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_sucursal TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_sucursal TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_sucursal BOOLEAN NOT NULL DEFAULT FALSE
);

-- La sucursal queda en users y en ticket.
ALTER TABLE users   ADD COLUMN IF NOT EXISTS sucursal_id BIGINT;
ALTER TABLE ticket  ADD COLUMN IF NOT EXISTS sucursal_id BIGINT;

CREATE INDEX IF NOT EXISTS ix_users_sucursal   ON users (sucursal_id);
CREATE INDEX IF NOT EXISTS ix_ticket_sucursal  ON ticket (sucursal_id);

-- Las claves foraneas se reponen en cada arranque en vez de usar
-- "ADD CONSTRAINT IF NOT EXISTS", que PostgreSQL no tiene. Es idempotente y no
-- deja ventana sin integridad porque el script corre antes de aceptar trafico.
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_sucursal;
ALTER TABLE users ADD CONSTRAINT fk_users_sucursal
    FOREIGN KEY (sucursal_id) REFERENCES sucursal (id_sucursal) ON DELETE SET NULL;

ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_sucursal;
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_sucursal
    FOREIGN KEY (sucursal_id) REFERENCES sucursal (id_sucursal) ON DELETE SET NULL;

-- ============================================================
-- TIPO DE TICKET (Incidentes, Requerimientos, Consultas, Proyectos...)
-- ============================================================
CREATE TABLE IF NOT EXISTS ticket_type (
    id_ticket_type          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_ticket_type        VARCHAR(100) NOT NULL UNIQUE,
    description_ticket_type VARCHAR(255),
    created_at_ticket_type  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_ticket_type  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_ticket_type     BOOLEAN NOT NULL DEFAULT FALSE
);

-- Columna tipo_ticket en ticket (nullable para no romper datos existentes)
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS type_ticket BIGINT;

-- Columna tipo_ticket en sla_rule (permite ligar reglas SLA a un tipo de ticket)
ALTER TABLE sla_rule ADD COLUMN IF NOT EXISTS type_ticket BIGINT;

-- Caducidad de la contraseña temporal (24h desde asignación)
ALTER TABLE users ADD COLUMN IF NOT EXISTS temporal_password_expires_at TIMESTAMPTZ;

-- ============================================================
-- 22. SMTP CONFIG (correo saliente configurable desde el panel)
-- ============================================================
CREATE TABLE IF NOT EXISTS smtp_config (
    id_smtp       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_smtp     VARCHAR(255) NOT NULL,
    port_smtp     INTEGER NOT NULL DEFAULT 587,
    username_smtp VARCHAR(255),
    password_smtp VARCHAR(255),
    from_address  VARCHAR(255),
    display_name  VARCHAR(255),
    use_tls       BOOLEAN NOT NULL DEFAULT TRUE,
    enabled_smtp  BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 23. REPORT_CONFIG (reportes personalizados defined por el admin)
-- Cada reporte guarda su propia consulta SQL; las columnas del PDF se
-- derivan de los alias de esa consulta. La tabla debia existir desde el
-- principio: los ALTER de icono/color mas abajo solo funcionaban sobre
-- bases que ya la tenian, y rompian el arranque en una base nueva.
-- ============================================================
CREATE TABLE IF NOT EXISTS report_config (
    id_report          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_report        VARCHAR(100) NOT NULL UNIQUE,
    title_report       VARCHAR(255) NOT NULL,
    description_report VARCHAR(500),
    query_sql          TEXT NOT NULL,
    active_report      BOOLEAN NOT NULL DEFAULT TRUE,
    icon_report        VARCHAR(100),
    color_report       VARCHAR(30),
    created_at_report  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_report  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_report     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_report_config_active ON report_config (active_report);

-- ============================================================
-- TAREAS / ACTIVIDADES (no-ticket) para kanban y calendario
-- ============================================================
CREATE TABLE IF NOT EXISTS task (
    id_task          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title_task       VARCHAR(255) NOT NULL,
    description_task TEXT,
    status_task      VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    assigned_user    BIGINT NOT NULL,
    created_by       BIGINT NOT NULL,
    start_task       TIMESTAMPTZ,
    due_task         TIMESTAMPTZ,
    duration_minutes INTEGER,
    color_task       VARCHAR(30) DEFAULT '#062e86',
    created_at_task  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_task  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_task     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_task_assigned FOREIGN KEY (assigned_user) REFERENCES users (id_user),
    CONSTRAINT fk_task_created  FOREIGN KEY (created_by) REFERENCES users (id_user)
);

-- ============================================================
-- LECTURA DE RESPUESTAS
-- ============================================================
-- Quien leyo cada respuesta y cuando. Antes solo secia que el mensaje se habia
-- enviado, pero no si el destinatario llego a abrirlo.
CREATE TABLE IF NOT EXISTS answer_read (
    id_answer_read BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    answer_id      BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    read_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_answer_read UNIQUE (answer_id, user_id),
    CONSTRAINT fk_answer_read_answer FOREIGN KEY (answer_id) REFERENCES answer (id_answer) ON DELETE CASCADE,
    CONSTRAINT fk_answer_read_user   FOREIGN KEY (user_id)   REFERENCES users (id_user)
);

-- ============================================================
-- ÍNDICES DE RENDIMIENTO
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_answer_read_answer ON answer_read (answer_id);
CREATE INDEX IF NOT EXISTS idx_ticket_user    ON ticket (user_ticket);
CREATE INDEX IF NOT EXISTS idx_ticket_agent   ON ticket (agent_ticket);
CREATE INDEX IF NOT EXISTS idx_ticket_status  ON ticket (status_ticket);
CREATE INDEX IF NOT EXISTS idx_ticket_created ON ticket (created_at_ticket);
CREATE INDEX IF NOT EXISTS idx_rating_ticket  ON ticket_rating (ticket_id);
CREATE INDEX IF NOT EXISTS idx_rating_user    ON ticket_rating (user_id);
CREATE INDEX IF NOT EXISTS idx_rating_month   ON ticket_rating (evaluation_month);
CREATE INDEX IF NOT EXISTS idx_eval_agent     ON monthly_evaluation (agent_id);
CREATE INDEX IF NOT EXISTS idx_eval_month     ON monthly_evaluation (evaluation_month);
CREATE INDEX IF NOT EXISTS idx_eval_status    ON monthly_evaluation (status);
CREATE INDEX IF NOT EXISTS idx_audit_ticket   ON ticket_audit (ticket_id);
CREATE INDEX IF NOT EXISTS idx_notif_status   ON notification (status_notification);
-- El navbar lista las notificaciones del usuario ordenadas por fecha en cada
-- carga de página. Sin este índice PostgreSQL hacía Seq Scan + Sort sobre toda
-- la tabla, y la tabla no para de crecer.
CREATE INDEX IF NOT EXISTS idx_notif_user_created
    ON notification (user_id, created_at_notification DESC);
-- El planificador de reintentos recorre las PENDING por orden de creación.
CREATE INDEX IF NOT EXISTS idx_notif_status_created
    ON notification (status_notification, created_at_notification);

-- ============================================================
-- INTENTOS DE LOGIN (bloqueo temporal tras 5 fallos)
-- ============================================================
CREATE TABLE IF NOT EXISTS login_attempt (
    id_login_attempt BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_key         VARCHAR(150) NOT NULL UNIQUE,
    failed_count     INTEGER NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- REFRESH TOKENS (rotación con hash; acceso JWT corto 15 min)
-- ============================================================
CREATE TABLE IF NOT EXISTS refresh_token (
    id_refresh_token BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    token_hash       VARCHAR(128) NOT NULL UNIQUE,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id_user)
);

CREATE INDEX IF NOT EXISTS idx_refresh_user  ON refresh_token (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_hash  ON refresh_token (token_hash);

-- ============================================================
-- AUDITORÍA (login, contraseñas, usuarios, configuración)
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id_audit     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(150),
    action       VARCHAR(50) NOT NULL,
    entity       VARCHAR(50),
    entity_id    BIGINT,
    ip_address   VARCHAR(50),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- La auditoría no era investigable con lo que tenía: sin saber qué cambió, sin
-- el método ni la ruta, y sin un identificador que la uniera con el log. Estos
-- campos son los que convierten una fila en "quién, qué, sobre qué, cuándo,
-- desde dónde y cómo se localiza en el log".
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS details     TEXT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS http_method VARCHAR(10);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_uri VARCHAR(300);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_id  VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_action  ON audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log (created_at);
-- Índice compuesto por usuario y fecha: es la pregunta más frecuente en una
-- investigación ("qué hizo esta persona"), y los índices por separado no la
-- responden sin recorrer la tabla entera.
CREATE INDEX IF NOT EXISTS idx_audit_user_created ON audit_log (username, created_at DESC);

-- Las tablas de auditoría son de solo inserción. Ninguna pantalla ni endpoint las
-- modifica, pero eso no protegía de quien llegara a la base con la cuenta de la
-- aplicación: un UPDATE o DELETE borraba el rastro sin dejar otro. Con estos
-- triggers, cambiar o borrar una fila falla siempre.
--
-- El cuerpo va entre comillas simples y no con dollar-quoting: el lector de
-- scripts de Spring respeta las comillas al partir por ';', pero no conoce esa
-- otra forma de citar (ver SqlScriptsValidationTest).
CREATE OR REPLACE FUNCTION auditoria_solo_insercion() RETURNS trigger AS
'BEGIN
    RAISE EXCEPTION ''La tabla % es de solo insercion: no se puede modificar ni borrar'', TG_TABLE_NAME;
END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_log_inmutable ON audit_log;
CREATE TRIGGER trg_audit_log_inmutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION auditoria_solo_insercion();
DROP TRIGGER IF EXISTS trg_audit_log_sin_truncate ON audit_log;
CREATE TRIGGER trg_audit_log_sin_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_solo_insercion();

DROP TRIGGER IF EXISTS trg_ticket_audit_inmutable ON ticket_audit;
CREATE TRIGGER trg_ticket_audit_inmutable BEFORE UPDATE OR DELETE ON ticket_audit
    FOR EACH ROW EXECUTE FUNCTION auditoria_solo_insercion();
DROP TRIGGER IF EXISTS trg_ticket_audit_sin_truncate ON ticket_audit;
CREATE TRIGGER trg_ticket_audit_sin_truncate BEFORE TRUNCATE ON ticket_audit
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_solo_insercion();

-- ============================================================
-- CONFIGURACIÓN IA (solo almacenamiento; el procesamiento es futuro)
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_config (
    id_ai_config      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    provider          VARCHAR(50) NOT NULL DEFAULT 'OPENAI',
    model             VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini',
    api_key           VARCHAR(500),
    base_url          VARCHAR(500),
    updated_by        BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
  );

-- ============================================================================
-- CIFRADO DE LA API KEY DE IA
-- ============================================================================
-- La api_key de la IA se guardaba en texto plano mientras el client_secret de
-- Microsoft Graph ya estaba cifrado con AES-GCM. Dos credenciales de proveedor
-- con el mismo riesgo de facturación, tratadas de forma distinta: con solo
-- lectura de la tabla, la clave de IA salía en claro y la de Graph no.
--
-- La migración no se puede hacer aquí porque el cifrado necesita la clave
-- maestra, que vive en el entorno y nunca en la base. La hace la aplicación al
-- arrancar. La columna marca si la fila ya fue revisada, para no reintentar en
-- cada inicio.
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS api_key_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_task_assigned  ON task (assigned_user);
CREATE INDEX IF NOT EXISTS idx_task_status    ON task (status_task);
CREATE INDEX IF NOT EXISTS idx_task_start     ON task (start_task);

-- ============================================================
-- MICROSOFT GRAPH: configuración de conexión (solo SUPER_ADMIN)
-- El client_secret se guarda CIFRADO simétricamente (AES-GCM).
-- La clave maestra vive en la variable de entorno GRAPH_ENCRYPTION_KEY.
-- ============================================================
CREATE TABLE IF NOT EXISTS microsoft_graph_config (
    id_graph_config          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                VARCHAR(150),
    client_id                VARCHAR(150),
    client_secret_encrypted  TEXT,
    scope                    VARCHAR(500) NOT NULL DEFAULT 'https://graph.microsoft.com/.default',
    grant_type               VARCHAR(50)  NOT NULL DEFAULT 'client_credentials',
    secret_expires_at        TIMESTAMPTZ,
    enabled                  BOOLEAN NOT NULL DEFAULT FALSE,
    sync_interval_minutes    INTEGER NOT NULL DEFAULT 1440,
    last_sync_at             TIMESTAMPTZ,
    updated_by               BIGINT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- AUDITORÍA DE LA CONFIGURACIÓN DE MICROSOFT GRAPH
-- Registra quién cambió qué (nunca el valor del secreto).
-- ============================================================
CREATE TABLE IF NOT EXISTS graph_config_audit (
    id_graph_audit    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_id         BIGINT,
    changed_by        BIGINT,
    changed_by_email  VARCHAR(150),
    action            VARCHAR(50) NOT NULL,
    details           TEXT,
    ip_address        VARCHAR(50),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_graph_audit_created ON graph_config_audit (created_at);

-- ============================================================
-- DATOS DE MICROSOFT 365 POR USUARIO (integrados en users)
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name           VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name             VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name              VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS photo_url              VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS user_principal_name    VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_active       BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_photo_etag   VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_synced       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_sync              TIMESTAMPTZ;

-- Corte de los tokens de acceso. El JWT no se guarda en ningún sitio, así que
-- revocar el refresh token no bastaba: el de acceso seguía valiendo hasta 15 min
-- después del logout o del cambio de contraseña. El filtro rechaza cualquier
-- token emitido antes de este instante. NULL = sin corte.
ALTER TABLE users ADD COLUMN IF NOT EXISTS tokens_valid_after     TIMESTAMPTZ;
-- ============================================================
-- COLUMNAS DE ICONO Y COLOR (badges, selectores y vistas)
-- Idempotentes: solo se anaden si la base todavia no las tiene.
-- ============================================================
ALTER TABLE area ADD COLUMN IF NOT EXISTS icon_area  VARCHAR(100);
ALTER TABLE area ADD COLUMN IF NOT EXISTS color_area VARCHAR(30);

ALTER TABLE support_team ADD COLUMN IF NOT EXISTS icon_team  VARCHAR(100);
ALTER TABLE support_team ADD COLUMN IF NOT EXISTS color_team VARCHAR(30);

ALTER TABLE ticket_type ADD COLUMN IF NOT EXISTS icon_ticket_type  VARCHAR(100);
ALTER TABLE ticket_type ADD COLUMN IF NOT EXISTS color_ticket_type VARCHAR(30);

ALTER TABLE sla_policy ADD COLUMN IF NOT EXISTS icon_sla_policy  VARCHAR(100);
ALTER TABLE sla_policy ADD COLUMN IF NOT EXISTS color_sla_policy VARCHAR(30);

ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS recipient_rule  VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255);
ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS icon_template  VARCHAR(100);
ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS color_template VARCHAR(30);

-- Imagen propia de la plantilla. Con ella se habilitan las variables
-- [imagen] (ya en HTML, lista para ponerla en el cuerpo) e [imagenUrl]
-- (solo la direccion), para que cada aviso lleve su propia ilustracion.
ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS image_url     VARCHAR(500);

-- Marca de "el administrador ya editó esta plantilla". Los scripts de arranque
-- (data.sql) solo rellenan el cuerpo y el destinatario de las plantillas que
-- siguenvirgiendo como semilla; en cuanto se guarda desde la pantalla de
-- plantillas queda marcada y los arranques dejan de tocarla. Sin esto, cada
-- reinicio revertía el texto, el asunto y la imagen que un admin había escrito.
ALTER TABLE notification_template ADD COLUMN IF NOT EXISTS custom_template BOOLEAN NOT NULL DEFAULT FALSE;

-- Reglas de la institucion para el asistente de IA: tono, limites, pasos
-- obligatorios y que nunca debe contestar. Se anteponen a las instrucciones
-- propias de cada accion, asi que una regla de aqui gana a la base del prompt.
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS rules_text TEXT;
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS enabled_actions VARCHAR(200) NOT NULL DEFAULT 'SUMMARY,ANSWER';

-- Las imagenes de las plantillas se guardan en la base, no en disco: asi el
-- despliegue no depende de una carpeta de archivos y la imagen no se pierde si
-- el servidor se reemplaza. Se identifican con un token aleatorio en vez de un
-- id correlativo porque el correo las carga sin sesion, y una ruta adivinable
-- permitiria recorrer todas las imagenes subidas por los administradores.
CREATE TABLE IF NOT EXISTS notification_template_image (
    id_image          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_image       VARCHAR(64) NOT NULL UNIQUE,
    id_template       BIGINT,
    mime_type_image   VARCHAR(100) NOT NULL,
    original_name_image VARCHAR(255),
    data_image        BYTEA NOT NULL,
    size_bytes_image  INTEGER NOT NULL,
    created_at_image  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_image_template FOREIGN KEY (id_template)
        REFERENCES notification_template (id_template) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_template_image_token ON notification_template_image (token_image);
CREATE INDEX IF NOT EXISTS ix_template_image_template ON notification_template_image (id_template);

ALTER TABLE report_config ADD COLUMN IF NOT EXISTS icon_report  VARCHAR(100);
ALTER TABLE report_config ADD COLUMN IF NOT EXISTS color_report VARCHAR(30);

ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS logo_login_url           VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS system_icon_url          VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS profile_cover_url        VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS chat_background_url      VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS chat_background_light_url VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS chat_background_dark_url  VARCHAR(500);
ALTER TABLE branding_config ADD COLUMN IF NOT EXISTS report_logo_url           VARCHAR(500);

-- ============================================================
-- COLUMNAS QUE FALTABAN EN LA BASE (fallo al arrancar en una BD nueva)
-- ============================================================
-- Estas tres columnas estan mapeadas en las entidades Java pero no estaban en
-- ningun CREATE TABLE. En una base ya migrada existian a mano, asi que el
-- problema no se veia; en una base creada desde cero, cualquier consulta que
-- tocara esas entidades reventaba con "no existe la columna ...". Por ejemplo,
-- crear un ticket consultaba la configuracion de Graph para saber si habia
-- correo habilitado y fallaba con un 500.
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS provider_icon VARCHAR(500);

-- mail_enabled es NOT NULL en la entidad, asi que necesita DEFAULT y un
-- UPDATE para las filas que ya existieran antes de esta columna.
ALTER TABLE microsoft_graph_config ADD COLUMN IF NOT EXISTS mail_enabled      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE microsoft_graph_config ADD COLUMN IF NOT EXISTS mail_from_account VARCHAR(255);
UPDATE microsoft_graph_config SET mail_enabled = FALSE WHERE mail_enabled IS NULL;


-- ============================================================
-- CONFIGURACION DE ENRUTAMIENTO (fila unica, edita el SUPER_ADMIN)
-- ============================================================
CREATE TABLE IF NOT EXISTS routing_config (
    id_routing_config         INTEGER PRIMARY KEY DEFAULT 1,
    auto_assign_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    notify_unassigned         BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at_routing_config TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_routing_config_singleton CHECK (id_routing_config = 1)
);

-- ============================================================
-- CONFIGURACION DE SESION: duracion del token de acceso
-- Antes la duracion estaba fija en application.yml, asi que no habia forma de
-- cambiarla sin recompilar ni reiniciar. Se guarda una unica fila con los
-- minutos de vida del token y desde cuantos minutos avisar antes de caducar.
CREATE TABLE IF NOT EXISTS session_config (
    id_session_config     INTEGER PRIMARY KEY DEFAULT 1,
    session_minutes       INTEGER NOT NULL DEFAULT 15,
    warning_minutes       INTEGER NOT NULL DEFAULT 5,
    auto_renew            BOOLEAN NOT NULL DEFAULT TRUE,
    idle_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    idle_minutes          INTEGER NOT NULL DEFAULT 15,
    updated_by            BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_config_singleton CHECK (id_session_config = 1),
    CONSTRAINT ck_session_config_minutes CHECK (session_minutes BETWEEN 5 AND 1440),
    CONSTRAINT ck_session_config_warning CHECK (warning_minutes >= 1 AND warning_minutes < session_minutes),
    CONSTRAINT ck_session_config_idle_minutes CHECK (idle_minutes BETWEEN 1 AND 1440)
);

-- La tabla ya existia en instalaciones previas, donde no habia columna de
-- inactividad: se agregan aparte para no tener que recrear el singleton.
ALTER TABLE session_config ADD COLUMN IF NOT EXISTS idle_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE session_config ADD COLUMN IF NOT EXISTS idle_minutes INTEGER NOT NULL DEFAULT 15;

INSERT INTO session_config (id_session_config, session_minutes, warning_minutes, auto_renew, idle_enabled, idle_minutes)
SELECT 1, 15, 5, TRUE, TRUE, 15
WHERE NOT EXISTS (SELECT 1 FROM session_config WHERE id_session_config = 1);

INSERT INTO routing_config (id_routing_config, auto_assign_enabled, notify_unassigned)
SELECT 1, TRUE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM routing_config WHERE id_routing_config = 1);

-- ============================================================
-- VISIBILIDAD DE LOS ACUSES DE LECTURA DEL CHAT
-- ============================================================
-- Quien puede ver, en el hilo de un ticket, quien ha leido cada mensaje
-- ("Leido por ..."). El valor es un codigo, no un booleano, porque los roles
-- admitidos por el negocio son combinaciones: solo ADMIN, todo el personal,
-- todos menos los usuarios, etc.
--
-- El valor NO decide si se guardan las marcas de lectura, solo si se muestran.
-- Guardarlas siempre es lo correcto: asi, si despues se amplia la visibilidad,
-- los acuses historicos ya estan ahi y no hay que reconstruir nada.
--
-- Valores admitidos (ver ChatVisibilityPolicy):
--   TODOS              cualquier usuario con acceso al ticket
--   TODOS_EXCEPTO_USUARIOS  admin, super_admin y agentes (no usuarios finales)
--   PERSONAL           super_admin, admin y agentes (igual que el anterior,
--                      se conserva por si la combinacion se separa despues)
--   ADMIN              super_admin y admin
--   SUPER_ADMIN        solo super_admin
--   AGENTES            solo agentes
CREATE TABLE IF NOT EXISTS chat_config (
    id_chat_config     INTEGER PRIMARY KEY DEFAULT 1,
    read_visible_to    VARCHAR(30) NOT NULL DEFAULT 'TODOS_EXCEPTO_USUARIOS',
    show_read_by_names BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by         BIGINT,
    created_at_chat    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_chat    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_config_singleton CHECK (id_chat_config = 1),
    CONSTRAINT ck_chat_config_read_visible CHECK (read_visible_to IN (
        'TODOS', 'TODOS_EXCEPTO_USUARIOS', 'PERSONAL', 'ADMIN', 'SUPER_ADMIN', 'AGENTES'
    ))
);

-- La tabla se creo sin show_read_by_names en instalaciones previas.
ALTER TABLE chat_config ADD COLUMN IF NOT EXISTS show_read_by_names BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO chat_config (id_chat_config, read_visible_to, show_read_by_names)
SELECT 1, 'TODOS_EXCEPTO_USUARIOS', TRUE
WHERE NOT EXISTS (SELECT 1 FROM chat_config WHERE id_chat_config = 1);
-- Estado de lectura de las notificaciones. Antes se reutilizaba
-- status_notification con el valor 'LEIDA', lo que pisaba el estado de envio:
-- una notificacion marcada como leida antes de enviarse impedia que el
-- despachador la encontrara y su correo nunca se entregaba.
ALTER TABLE notification ADD COLUMN IF NOT EXISTS read_notification    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS read_at_notification TIMESTAMPTZ;

-- Las filas historicas que quedaron en 'LEIDA' eran en realidad entregadas.
UPDATE notification SET status_notification = 'SENT' WHERE status_notification = 'LEIDA';

-- ============================================================================
-- PAGINAS DE ERROR Y POLITICA DE PRIVACIDAD (EDITABLES DESDE EL PANEL)
-- ============================================================================
-- Los textos de las pantallas 404, 403, 500 y 503, y el cuerpo de la politica
-- de privacidad, los edita el super administrador. Antes vivian en el codigo del
-- frontend, asi que cambiarlos obligaba a compilar y desplegar de nuevo.
--
-- Se guardan en tablas singleton (una sola fila, id fijo en 1) porque no tienen
-- historial ni se multiplican: lo que se busca es "el texto vigente". Un CHECK
-- en la base impide que aparezca una segunda fila por error.
--
-- Los textos por defecto van en data.sql, no aqui, y sembrados con
-- WHERE NOT EXISTS para no pisar lo que el super administrador ya edito. El
-- cuerpo de privacidad usa una columna boolean de "editado" (custom_privacy)
-- porque es un texto largo que puede quedar desfasado en el repositorio: sin
-- en ese momento el seed vuelve a imponer la version del repo en cada arranque.
CREATE TABLE IF NOT EXISTS error_page_config (
    id_error_page       INTEGER PRIMARY KEY DEFAULT 1,

    error_404_title     VARCHAR(160) NOT NULL DEFAULT 'Pagina no encontrada',
    error_404_message   VARCHAR(1000) NOT NULL DEFAULT 'La direccion que buscas no existe o fue movida. Revisa el enlace o vuelve al inicio.',
    error_404_icon      VARCHAR(120) NOT NULL DEFAULT 'fa-solid fa-compass',
    error_404_color     VARCHAR(7)   NOT NULL DEFAULT '#64748b',

    error_403_title     VARCHAR(160) NOT NULL DEFAULT 'Acceso no autorizado',
    error_403_message   VARCHAR(1000) NOT NULL DEFAULT 'Tu perfil no tiene permiso para ver esta seccion. Si crees que es un error, solicita el acceso al administrador.',
    error_403_icon      VARCHAR(120) NOT NULL DEFAULT 'fa-solid fa-lock',
    error_403_color     VARCHAR(7)   NOT NULL DEFAULT '#d97706',

    error_500_title     VARCHAR(160) NOT NULL DEFAULT 'Error interno del servidor',
    error_500_message   VARCHAR(1000) NOT NULL DEFAULT 'Ocurrio un problema inesperado en el servidor. El equipo tecnico ya fue notificado.',
    error_500_icon      VARCHAR(120) NOT NULL DEFAULT 'fa-solid fa-triangle-exclamation',
    error_500_color     VARCHAR(7)   NOT NULL DEFAULT '#dc2626',

    error_503_title     VARCHAR(160) NOT NULL DEFAULT 'Servicio no disponible',
    error_503_message   VARCHAR(1000) NOT NULL DEFAULT 'El sistema esta temporalmente fuera de servicio por mantenimiento. Vuelve a intentar en unos minutos.',
    error_503_icon      VARCHAR(120) NOT NULL DEFAULT 'fa-solid fa-screwdriver-wrench',
    error_503_color     VARCHAR(7)   NOT NULL DEFAULT '#2563eb',

    updated_by          BIGINT,
    created_at_error    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_error    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_error_page_config_singleton CHECK (id_error_page = 1)
);

CREATE TABLE IF NOT EXISTS privacy_config (
    id_privacy_config   INTEGER PRIMARY KEY DEFAULT 1,

    privacy_title       VARCHAR(160) NOT NULL DEFAULT 'Politica de privacidad',
    privacy_version     VARCHAR(30)  NOT NULL DEFAULT '1.0',
    -- El cuerpo va en markdown y se renderiza en el frontend, asi que es TEXT y
    -- no VARCHAR: una politica de verdad no cabe en 1000 caracteres.
    privacy_content     TEXT,
    privacy_contact     VARCHAR(160),
    privacy_effective   VARCHAR(60),
    -- Marca de que el super administrador edito el cuerpo. Mientras sea FALSE el
    -- seed de data.sql puede actualizar el texto; en cuanto pasa a TRUE el
    -- contenido es del administrador y el seed ya no lo toca.
    custom_privacy      BOOLEAN NOT NULL DEFAULT FALSE,

    updated_by          BIGINT,
    created_at_privacy  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_privacy  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_privacy_config_singleton CHECK (id_privacy_config = 1)
);

-- ============================================================
-- RESTABLECIMIENTO DE CONTRASEÑA (confirmación por enlace)
-- ============================================================
-- Antes, POST /auth/forgot-password cambiaba la contraseña en el acto y la
-- mandaba por correo. Eso no demuestra nada: bastaba con conocer el correo
-- corporativo de un compañero (son consecutivos y se deducen) para tumbarle
-- la sesión y forzarle un cambio. Ahora el correo lleva un enlace, y solo al
-- abrirlo se toca la contraseña.
--
-- Se guarda el SHA-256 del token, nunca el token: si alguien lee la base, los
-- enlaces pendientes no le sirven. `used` obliga a un solo uso, y `expires_at`
-- acota la ventana por si el correo llega tarde.
CREATE TABLE IF NOT EXISTS password_reset_token (
    id_password_reset_token BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(128) NOT NULL UNIQUE,
    expires_at         TIMESTAMPTZ NOT NULL,
    used               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pw_reset_user FOREIGN KEY (user_id) REFERENCES users (id_user)
);

CREATE INDEX IF NOT EXISTS idx_pw_reset_user  ON password_reset_token (user_id);
CREATE INDEX IF NOT EXISTS idx_pw_reset_hash  ON password_reset_token (token_hash);
-- ============================================================
-- ROTACIÓN DE REFRESH TOKEN (detección de reutilización)
-- ============================================================
-- El refresh token roba su valor con el único fin de reponerlo, así que tener
-- uno no sirve de mucho: hay que devolverlo. Eso es lo que hace esta columna:
-- al rotar, el token viejo apunta al nuevo. Si un token ya rotado vuelve a
-- aparecer, no es un reintento de red ni un doble clic: hay dos manos con el
-- mismo token, y la segunda cierra todas las sesiones de ese usuario.
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS replaced_by_hash VARCHAR(128);

-- Marca el instante de la deteccion para no repetir el aviso en cada intento
-- posterior del mismo atacante (que va a seguir insistiendo).
ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS reuse_detected_at TIMESTAMPTZ;
-- ============================================================
-- MFA / TOTP (doble factor)
-- ============================================================
-- El super administrador activa el doble factor con un interruptor global. Al
-- estar activo, usuario, agente y admin tienen que confirmar un codigo de su
-- aplicacion de autenticacion ademas de su contrasena.
--
-- mfa_setting es una tabla singleton (id fijo 1), igual que session_config: asi
-- el interruptor se guarda en la base y el administrador puede cambiarlo sin
-- reiniciar. Se siembla en data.sql.
CREATE TABLE IF NOT EXISTS mfa_setting (
    id_mfa_setting   BIGINT PRIMARY KEY,
    mfa_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    -- Lo que aparece como nombre de la cuenta en la app del usuario.
    issuer_mfa       VARCHAR(120) NOT NULL DEFAULT 'Mesa de Servicios',
    updated_at_mfa   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_mfa   BIGINT,
    CONSTRAINT uq_mfa_setting_singleton CHECK (id_mfa_setting = 1)
);

-- Un registro por usuario. secret_almacenado queda cifrado (SecretCryptoService);
-- la tabla no guarda nunca el secreto en claro, ni siquiera el pendiente de
-- enrolar.
--
-- enrolled distingue dos estados que antes se confundian: tener un secreto
-- guardado NO significa estar enrolado. El secreto se genera en el primer login
-- con MFA activo y pasa a secret_almacenado solo cuando el usuario teclea un
-- codigo valido. Asi, si alguien empezo a enrolar y lo dejo a medias, no queda
-- un secreto a medias que el usuario ya no puede leer.
CREATE TABLE IF NOT EXISTS user_mfa (
    id_user_mfa            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                BIGINT NOT NULL UNIQUE,
    secret_almacenado      TEXT,
    secret_pendiente       TEXT,
    enrolled               BOOLEAN NOT NULL DEFAULT FALSE,
    -- Permite al usuario apagar su propio doble factor con una prueba valida.
    -- Si es FALSE, solo el super administrador puede quitarlo.
    desactivable_usuario   BOOLEAN NOT NULL DEFAULT TRUE,
    -- Ultimo paso de tiempo ya aceptado. Impide que un codigo interceptedo
    -- sirva dos veces: las apps autentican cada 30 segundos y el mismo codigo se
    -- repetiria hasta 3 veces si se aceptara.
    ultimo_contador        BIGINT,
    creado_at_user_mfa     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verificado_at_user_mfa TIMESTAMPTZ,
    CONSTRAINT fk_user_mfa_user FOREIGN KEY (user_id) REFERENCES users (id_user)
);

-- Desafio de un solo uso. Cuando el MFA esta activo, el login NO devuelve tokens:
-- devuelve un desafio, y los tokens solo salen al confirmar el codigo. Sin este
-- paso intermedio, el segundo factor no impediría nada, porque la contraseña
 -- sola bastaría.
CREATE TABLE IF NOT EXISTS mfa_challenge (
    id_mfa_challenge BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    token_hash       VARCHAR(128) NOT NULL UNIQUE,
    -- ENROLL: el usuario aun no esta enrolado y hay que confirmar el alta.
    -- LOGIN: ya esta enrolado y hay que confirmar el codigo.
    purpose          VARCHAR(16) NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    used_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mfa_challenge_user FOREIGN KEY (user_id) REFERENCES users (id_user)
);

CREATE INDEX IF NOT EXISTS idx_mfa_challenge_hash ON mfa_challenge (token_hash);

-- Codigos de recuperacion: por si el usuario pierde el movil. Se guardan
-- cifrados y de uno en uno marcados como usados, para que cada codigo sirva una
-- sola vez y se sepa cuales quedan.
CREATE TABLE IF NOT EXISTS mfa_recovery_code (
    id_mfa_recovery      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT NOT NULL,
    code_hash            VARCHAR(128) NOT NULL,
    usado_at             TIMESTAMPTZ,
    creado_at_mfa        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES users (id_user)
);

CREATE INDEX IF NOT EXISTS idx_mfa_recovery_user ON mfa_recovery_code (user_id);

-- ==============================================================================
-- MOTOR DE WORKFLOWS
-- ==============================================================================
-- Un workflow es un GRAFO dirigido aciclico de nodos, no una lista de pasos: el
-- IF tiene varias salidas y el SWITCH tiene una por rama. La definicion vive en
-- JSONB dentro de la version, un documento por version.
--
-- versionado
-- -----------
-- Las versiones son INMUTABLES en cuanto se publican. Editar un workflow activo
-- crea una version nueva y las ejecuciones en vuelo siguen apuntando a la suya,
-- porque cada ejecucion guarda su id de version. Si la definicion de una version
-- publicada se pudiera editar, una ejecucion a medio camino cambiaria de reglas
-- bajo los pies y el resultado seria impredecible.
--
-- indices unicos PARCIALES
-- ------------------------
-- is_active_version solo admite UN TRUE por workflow. No es una comprobacion de
-- aplicacion: es una restriccion de base, asi que dos activaciones simultaneas
-- no pueden dejar dos versiones vigentes.
--
CREATE TABLE IF NOT EXISTS workflow (
    id_workflow              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name_workflow            VARCHAR(120) NOT NULL,
    description_workflow     VARCHAR(500),
    -- BORRADOR: aun no se puede activar. ACTIVO: se ejecuta. INACTIVO: pausado a
    -- proposito. ERROR: el motor lo marca si la definicion deja de ser valida.
    status_workflow          VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    -- Tipo de disparador: TICKET_CREATED, TICKET_STATUS_CHANGED, SCHEDULE...
    trigger_type             VARCHAR(40),
    trigger_config           JSONB,
    -- Version vigente. Es una copia denormalizada para no tener que contar
    -- versions activas en cada lectura del constructor.
    current_version_workflow INTEGER NOT NULL DEFAULT 1,
    -- Limite de autoridade: el workflow ejecuta con los permisos que tenia su
    -- autor, nunca con mas. Ver WorkflowProperties y ActorScope.
    authority_scope          VARCHAR(20) NOT NULL DEFAULT 'SUPER_ADMIN',
    created_by_workflow      BIGINT NOT NULL,
    updated_by_workflow      BIGINT,
    last_run_workflow        TIMESTAMPTZ,
    run_count_workflow       BIGINT NOT NULL DEFAULT 0,
    created_at_workflow      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_workflow      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_workflow         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_workflow_status
        CHECK (status_workflow IN ('BORRADOR', 'ACTIVO', 'INACTIVO', 'ERROR')),
    CONSTRAINT fk_workflow_creado_por FOREIGN KEY (created_by_workflow) REFERENCES users (id_user)
);

-- El nombre se libera al borrar en caliente: dos workflows en papel distintos no
-- pueden pelearse por el mismo nombre y, al reutilizarlo, el nombre vuelve a estar
-- disponible.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_name
    ON workflow (name_workflow) WHERE deleted_workflow = FALSE;

CREATE INDEX IF NOT EXISTS idx_workflow_status ON workflow (status_workflow);
CREATE INDEX IF NOT EXISTS idx_workflow_trigger ON workflow (trigger_type);

-- ==============================================================================
-- VERSIONES (inmutables al publicar)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS workflow_version (
    id_workflow_version  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_workflow    BIGINT NOT NULL,
    version_workflow     INTEGER NOT NULL,
    -- Grafo completo: nodos, conectores, metadatos. Un documento por version.
    definition_workflow  JSONB NOT NULL,
    -- Solo una vigente por workflow. Indice unico PARCIAL: varias versiones
    -- inactivas conviven sin molestarse, pero dos vigentes son imposibles.
    is_active_version    BOOLEAN NOT NULL DEFAULT FALSE,
    published_at_workflow TIMESTAMPTZ,
    created_by_workflow  BIGINT NOT NULL,
    created_at_workflow  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workflow_version_num
        UNIQUE (workflow_workflow, version_workflow),
    CONSTRAINT ck_workflow_version_positive CHECK (version_workflow > 0),
    CONSTRAINT fk_wf_version_wf FOREIGN KEY (workflow_workflow) REFERENCES workflow (id_workflow),
    CONSTRAINT fk_wf_version_creado_por FOREIGN KEY (created_by_workflow) REFERENCES users (id_user)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_version_activa
    ON workflow_version (workflow_workflow) WHERE is_active_version = TRUE;

CREATE INDEX IF NOT EXISTS idx_wf_version_workflow ON workflow_version (workflow_workflow);

-- ==============================================================================
-- EJECUCIONES
-- ==============================================================================
CREATE TABLE IF NOT EXISTS workflow_execution (
    id_workflow_execution      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_workflow          BIGINT NOT NULL,
    -- Apunta a la version usada. NO a la vigente: por eso editar un workflow
    -- activo no altera lo que hace una ejecucion que ya empezo.
    version_workflow           BIGINT NOT NULL,
    trigger_type               VARCHAR(40) NOT NULL,
    -- Clave de deduplicacion. Es lo que corta los bucles; ver
    -- workflow_event_dedup.
    correlation_key            VARCHAR(120) NOT NULL,
    -- Evento original, congelado. Se guarda para poder depurar una ejecucion
    -- meses despues aunque el ticket haya cambiado.
    trigger_payload            JSONB,
    -- PENDIENTE / EJECUTANDO / ESPERANDO / APROBACION / COMPLETADO / ERROR /
    -- CANCELADO
    status_execution           VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    current_node_execution     VARCHAR(60),
    -- Contexto vivo de variables. Aplanado con claves de puntos.
    variables_execution        JSONB,
    result_execution           JSONB,
    error_node_execution       VARCHAR(60),
    -- Nunca se muestra al usuario final; solo en el historial de ejecuciones.
    error_message_execution    VARCHAR(1000),
    -- Se hereda entre workflows encadenados: A dispara B, B dispara C. Al pasar de
    -- 5 se corta la cascada.
    depth_execution            INTEGER NOT NULL DEFAULT 0,
    -- NULL cuando la arranco el sistema; informado cuando fue manual.
    started_by_execution       BIGINT,
    attempts_execution         INTEGER NOT NULL DEFAULT 0,
    started_at_execution       TIMESTAMPTZ,
    finished_at_execution      TIMESTAMPTZ,
    created_at_execution       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workflow_execution_status
        CHECK (status_execution IN ('PENDIENTE', 'EJECUTANDO', 'ESPERANDO',
                                    'APROBACION', 'COMPLETADO', 'ERROR', 'CANCELADO')),
    CONSTRAINT ck_workflow_execution_depth CHECK (depth_execution >= 0),
    CONSTRAINT fk_wf_exec_wf FOREIGN KEY (workflow_workflow) REFERENCES workflow (id_workflow),
    CONSTRAINT fk_wf_exec_version FOREIGN KEY (version_workflow) REFERENCES workflow_version (id_workflow_version),
    CONSTRAINT fk_wf_exec_iniciado_por FOREIGN KEY (started_by_execution) REFERENCES users (id_user)
);

-- El worker busca por (status, created_at): sin este indice cada barrido
-- seria un escaneo completo de la tabla.
CREATE INDEX IF NOT EXISTS idx_wf_exec_pendientes
    ON workflow_execution (status_execution, created_at_execution);

CREATE INDEX IF NOT EXISTS idx_wf_exec_workflow
    ON workflow_execution (workflow_workflow, created_at_execution DESC);

-- ==============================================================================
-- RECORRIDO NODO A NODO
-- ==============================================================================
-- Una fila por nodo recorrido. Sin esto, depurar una automatizacion es adivinar:
-- solo quedaria el estado final y el error.
CREATE TABLE IF NOT EXISTS workflow_node_run (
    id_node_run            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execution_execution    BIGINT NOT NULL,
    node_id_node_run       VARCHAR(60) NOT NULL,
    node_type_node_run     VARCHAR(40) NOT NULL,
    status_node_run        VARCHAR(20) NOT NULL DEFAULT 'EJECUTANDO',
    input_node_run         JSONB,
    output_node_run        JSONB,
    -- Para diagnostico. Igual que en la ejecucion, nunca al usuario.
    error_message_node_run VARCHAR(1000),
    -- Orden dentro de la ejecucion, para reconstruir el recorrido.
    sequence_node_run      INTEGER NOT NULL DEFAULT 0,
    started_at_node_run    TIMESTAMPTZ,
    finished_at_node_run   TIMESTAMPTZ,
    created_at_node_run    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_node_run_status
        CHECK (status_node_run IN ('EJECUTANDO', 'COMPLETADO', 'ERROR', 'OMITIDO')),
    CONSTRAINT fk_node_run_exec FOREIGN KEY (execution_execution) REFERENCES workflow_execution (id_workflow_execution)
);

CREATE INDEX IF NOT EXISTS idx_node_run_exec
    ON workflow_node_run (execution_execution, sequence_node_run);

-- ==============================================================================
-- ESPERAS Y APROBACIONES
-- ==============================================================================
-- Un nodo que no puede continuar deja una fila aqui y el worker no lo toca hasta
-- que sea elegible. Esto es lo que permite esperar 24 horas o una aprobacion
-- sin ocupar un hilo ni un timer en memoria.
CREATE TABLE IF NOT EXISTS workflow_wait (
    id_wait              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execution_execution  BIGINT NOT NULL,
    node_id_wait         VARCHAR(60) NOT NULL,
    -- DELAY: dormir X. UNTIL: esperar a una fecha. APPROVAL: esperar a alguien.
    kind_wait            VARCHAR(20) NOT NULL,
    resume_at_wait       TIMESTAMPTZ,
    -- Para APPROVAL: quien puede decidir. Se comprueba contra RBAC en el momento
    -- de decidir, no en el de crear, por si el rol cambia entre medias.
    approver_role_wait   VARCHAR(20),
    approver_user_wait   BIGINT,
    status_wait          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by_wait      BIGINT,
    decided_at_wait      TIMESTAMPTZ,
    comment_wait         VARCHAR(500),
    created_at_wait      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workflow_wait_kind
        CHECK (kind_wait IN ('DELAY', 'UNTIL', 'APPROVAL')),
    CONSTRAINT ck_workflow_wait_status
        CHECK (status_wait IN ('PENDING', 'APPROVED', 'REJECTED', 'TIMEOUT')),
    -- Una espera viva siempre tiene fecha de reanudacion: si no, el worker no
    -- sabria cuando volver a mirarla.
    CONSTRAINT ck_workflow_wait_resume
        CHECK (kind_wait = 'APPROVAL' OR resume_at_wait IS NOT NULL),
    CONSTRAINT fk_workflow_wait_exec FOREIGN KEY (execution_execution) REFERENCES workflow_execution (id_workflow_execution),
    CONSTRAINT fk_workflow_wait_approver_user FOREIGN KEY (approver_user_wait) REFERENCES users (id_user),
    CONSTRAINT fk_workflow_wait_decided_by FOREIGN KEY (decided_by_wait) REFERENCES users (id_user)
);

CREATE INDEX IF NOT EXISTS idx_workflow_wait_pendientes
    ON workflow_wait (status_wait, resume_at_wait);

-- ==============================================================================
-- DEDUPLICACION: el muro contra los bucles
-- ==============================================================================
-- Workflow A actualiza un ticket, eso dispara "ticket actualizado", que vuelve a
-- disparar A, que actualiza otra vez...
--
-- Aqui esta la garantia de verdad: la restriccion es de BASE, no de aplicacion. Un
-- SELECT previo seguido de un INSERT no serviria, porque dos hilos pueden pasar
-- el SELECT a la vez. Con la clave unica, uno de los dos gana y el otro recibe un
-- error de clave duplicada que el motor interpreta como "ya lo he hecho".
--
-- Por eso la clave incluye el TIPO de evento y no solo el id: cambiar un ticket
-- dispara ticket.updated, que es una clave distinta de ticket.created. Por eso
-- tambien hacen falta el limite de profundidad y el tope de pasos; esta tabla
-- evita repeticiones del MISMO evento, no el ciclo A-B-A-B.
--
CREATE TABLE IF NOT EXISTS workflow_event_dedup (
    id_event_dedup            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_workflow         BIGINT NOT NULL,
    correlation_key           VARCHAR(120) NOT NULL,
    execution_execution       BIGINT NOT NULL,
    created_at_event_dedup    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workflow_event_dedup
        UNIQUE (workflow_workflow, correlation_key),
    CONSTRAINT fk_event_dedup_wf FOREIGN KEY (workflow_workflow) REFERENCES workflow (id_workflow),
    CONSTRAINT fk_event_dedup_exec FOREIGN KEY (execution_execution) REFERENCES workflow_execution (id_workflow_execution)
);

CREATE INDEX IF NOT EXISTS idx_event_dedup_created ON workflow_event_dedup (created_at_event_dedup);

-- ==============================================================================
-- AMPLIACIONES DEL MOTOR (Fase 2)
-- ==============================================================================
-- Puerto por el que retoma una ejecución que vuelve de una pausa. Antes se
-- retomaba en el propio nodo WAIT o APPROVAL, que volvía a pausar y la ejecución
-- no salía nunca de la espera. NEXT (espera vencida), APROBADO, RECHAZADO o VENCIDO.
ALTER TABLE workflow_execution ADD COLUMN IF NOT EXISTS resume_port_execution VARCHAR(30);
-- Entidad a la que se refiere la ejecución (ticket, usuario), para enlazarla
-- desde el historial y filtrar por ella.
ALTER TABLE workflow_execution ADD COLUMN IF NOT EXISTS entity_type_execution VARCHAR(30);
ALTER TABLE workflow_execution ADD COLUMN IF NOT EXISTS entity_id_execution   BIGINT;
-- Ejecución cuya acción provocó el evento que creó esta. Es lo que permite medir
-- la profundidad de una cascada A -> B -> C y cortar los bucles.
ALTER TABLE workflow_execution ADD COLUMN IF NOT EXISTS origin_execution      BIGINT;
CREATE INDEX IF NOT EXISTS idx_wf_exec_entidad
    ON workflow_execution (entity_type_execution, entity_id_execution);

-- Próxima ejecución de un workflow con disparador programado.
ALTER TABLE workflow ADD COLUMN IF NOT EXISTS next_run_at_workflow TIMESTAMPTZ;

-- ==============================================================================
-- BUSQUEDA GLOBAL (Fase 3)
-- ==============================================================================
-- La búsqueda global (GET /search) filtra por texto los campos de cada entidad.
-- Sin pg_trgm cada consulta sería un escaneo completo; con el índice GIN por
-- trigramas el LIKE '%termino%' recorre un árbol y no la tabla entera. La
-- extensión es idempotente y no requiere privilegios especiales; si en algún
-- entorno no estuviera permitido crearla, bajarían estos índices pero la
-- búsqueda seguiría funcionando.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Tickets: la búsqueda filtra sobre título y descripción.
CREATE INDEX IF NOT EXISTS idx_ticket_trgm_titulo
    ON ticket USING GIN (title_ticket gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ticket_trgm_descripcion
    ON ticket USING GIN (description_ticket gin_trgm_ops);

-- Personas: la búsqueda filtra sobre correo y nombre. La identificación no se
-- indexa porque la búsqueda global no la usa (ver SearchService).
CREATE INDEX IF NOT EXISTS idx_users_trgm_correo
    ON users USING GIN (corporate_email_user gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_trgm_nombre
    ON users USING GIN (display_name gin_trgm_ops);

-- Equipos y áreas: la búsqueda filtra por su nombre.
CREATE INDEX IF NOT EXISTS idx_team_trgm_nombre
    ON support_team USING GIN (name_team gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_area_trgm_nombre
    ON area USING GIN (name_area gin_trgm_ops);

-- Workflows: la búsqueda filtra por nombre y descripción.
CREATE INDEX IF NOT EXISTS idx_workflow_trgm_nombre
    ON workflow USING GIN (name_workflow gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_workflow_trgm_descripcion
    ON workflow USING GIN (description_workflow gin_trgm_ops);
