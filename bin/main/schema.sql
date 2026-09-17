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
-- ÍNDICES DE RENDIMIENTO
-- ============================================================
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

CREATE INDEX IF NOT EXISTS idx_audit_action  ON audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log (created_at);

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