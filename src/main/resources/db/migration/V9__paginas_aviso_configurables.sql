-- ============================================================================
-- PÁGINAS DE AVISO: BOTÓN, CÓDIGO Y ENLACE A PRIVACIDAD CONFIGURABLES
-- ============================================================================
-- Con el diseño nuevo de las páginas de error (código grande + texto) quedaban
-- fijos en el código el texto del botón, si se muestra el código ("404") y si
-- aparece el enlace a la política de privacidad. Ahora se configuran desde
-- Páginas del Sistema y valen para todas las páginas de aviso.
-- ============================================================================

ALTER TABLE error_page_config ADD COLUMN IF NOT EXISTS error_button_text VARCHAR(60) NOT NULL DEFAULT 'Volver al inicio';
ALTER TABLE error_page_config ADD COLUMN IF NOT EXISTS error_show_code BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE error_page_config ADD COLUMN IF NOT EXISTS error_show_privacy BOOLEAN NOT NULL DEFAULT TRUE;
