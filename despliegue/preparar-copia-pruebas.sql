-- ============================================================================
-- Preparar una COPIA de producción para usarla en pruebas.
--
-- Ejecutar SIEMPRE justo después de restaurar la copia y ANTES de arrancar el
-- backend de pruebas. Sin esto, el entorno de pruebas trabajaría con los datos
-- reales como si fuera producción: enviaría correos a los usuarios, ordenaría
-- llamadas a la central y ejecutaría las automatizaciones.
--
--   psql -U postgres -d <base_de_pruebas> -f preparar-copia-pruebas.sql
--
-- No toca los tickets ni los usuarios: solo apaga lo que sale hacia fuera.
-- (audit_log y ticket_audit no se modifican: son de solo inserción.)
-- ============================================================================

BEGIN;

-- 1. Correo: apagado y cola vaciada (lo pendiente se habría enviado al arrancar).
UPDATE microsoft_graph_config SET mail_enabled = FALSE;
UPDATE mail_outbox SET status_mail = 'CANCELADO'
WHERE status_mail IN ('PENDIENTE', 'ENVIANDO');

-- 2. Microsoft Graph: sin sincronización de usuarios contra el tenant real.
UPDATE microsoft_graph_config SET enabled = FALSE;

-- 3. Telefonía: apagada y sin token (el puente real apunta a producción).
UPDATE telefonia_config SET enabled_telefonia = FALSE, token_hash = NULL, token_creado_at = NULL;
UPDATE llamada SET status_llamada = 'VENCIDA'
WHERE status_llamada IN ('PENDIENTE', 'ENVIADA');

-- 4. Automatizaciones: desactivadas y sin ejecuciones en cola.
UPDATE workflow SET status_workflow = 'INACTIVO' WHERE status_workflow = 'ACTIVO';
UPDATE workflow_execution SET status_execution = 'CANCELADO'
WHERE status_execution IN ('PENDIENTE', 'EJECUTANDO', 'ESPERANDO', 'APROBACION');

COMMIT;

-- Comprobación rápida: todo debe salir en false / 0.
SELECT 'correo activo' AS que, bool_or(mail_enabled)::text AS valor FROM microsoft_graph_config
UNION ALL SELECT 'graph activo', bool_or(enabled)::text FROM microsoft_graph_config
UNION ALL SELECT 'telefonia activa', bool_or(enabled_telefonia)::text FROM telefonia_config
UNION ALL SELECT 'correos pendientes', count(*)::text FROM mail_outbox WHERE status_mail IN ('PENDIENTE', 'ENVIANDO')
UNION ALL SELECT 'workflows activos', count(*)::text FROM workflow WHERE status_workflow = 'ACTIVO';
