-- ============================================================================
-- PÁGINAS DE ERROR: TEXTOS DE FÁBRICA CON TILDES
-- ============================================================================
-- Los textos por defecto de las páginas 404/403/500/503 se escribieron sin
-- tildes ("Pagina no encontrada", "esta seccion", "Ocurrio"...). Es lo primero
-- que ve alguien cuando algo falla, así que se corrigen.
--
-- Solo se cambia un texto si sigue siendo EXACTAMENTE el de fábrica: si un
-- administrador lo personalizó desde Páginas del Sistema, es suyo y se respeta.
-- También se corrigen los DEFAULT de columna, que vienen de V1 y no se pueden
-- editar allí.
-- ============================================================================

UPDATE error_page_config
SET error_404_title = 'Página no encontrada'
WHERE error_404_title = 'Pagina no encontrada';

UPDATE error_page_config
SET error_404_message = 'La dirección que buscas no existe o fue movida. Revisa el enlace o vuelve al inicio.'
WHERE error_404_message = 'La direccion que buscas no existe o fue movida. Revisa el enlace o vuelve al inicio.';

UPDATE error_page_config
SET error_403_message = 'Tu perfil no tiene permiso para ver esta sección. Si crees que es un error, solicita el acceso al administrador.'
WHERE error_403_message = 'Tu perfil no tiene permiso para ver esta seccion. Si crees que es un error, solicita el acceso al administrador.';

UPDATE error_page_config
SET error_500_message = 'Ocurrió un problema inesperado en el servidor. El equipo técnico ya fue notificado.'
WHERE error_500_message = 'Ocurrio un problema inesperado en el servidor. El equipo tecnico ya fue notificado.';

UPDATE error_page_config
SET error_503_message = 'El sistema está temporalmente fuera de servicio por mantenimiento. Vuelve a intentar en unos minutos.'
WHERE error_503_message = 'El sistema esta temporalmente fuera de servicio por mantenimiento. Vuelve a intentar en unos minutos.';

ALTER TABLE error_page_config
    ALTER COLUMN error_404_title SET DEFAULT 'Página no encontrada',
    ALTER COLUMN error_404_message SET DEFAULT 'La dirección que buscas no existe o fue movida. Revisa el enlace o vuelve al inicio.',
    ALTER COLUMN error_403_message SET DEFAULT 'Tu perfil no tiene permiso para ver esta sección. Si crees que es un error, solicita el acceso al administrador.',
    ALTER COLUMN error_500_message SET DEFAULT 'Ocurrió un problema inesperado en el servidor. El equipo técnico ya fue notificado.',
    ALTER COLUMN error_503_message SET DEFAULT 'El sistema está temporalmente fuera de servicio por mantenimiento. Vuelve a intentar en unos minutos.';

-- ----------------------------------------------------------------------------
-- Borrador de la política de privacidad, solo si nadie lo ha personalizado
-- (custom_privacy = FALSE). Un texto propio de la institución no se toca.
-- ----------------------------------------------------------------------------
UPDATE privacy_config
SET privacy_title = CASE WHEN privacy_title = 'Politica de privacidad' THEN 'Política de privacidad' ELSE privacy_title END,
    privacy_content = replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(privacy_content,
        '# Politica de privacidad', '# Política de privacidad'),
        'Sustituyala por el documento', 'Sustitúyala por el documento'),
        '> institucion antes de publicarlo.', '> institución antes de publicarlo.'),
        'Indique que entidad es la responsable', 'Indique qué entidad es la responsable'),
        'personales y a que direccion de correo', 'personales y a qué dirección de correo'),
        '## 2. Que datos se recogen', '## 2. Qué datos se recogen'),
        'Describa las categorias de datos', 'Describa las categorías de datos'),
        '## 3. Para que se usan', '## 3. Para qué se usan'),
        'recibidas a traves de la', 'recibidas a través de la'),
        'el control de su atencion y cierre.', 'el control de su atención y cierre.'),
        '## 4. Base juridica', '## 4. Base jurídica'),
        'Indique la base juridica del tratamiento', 'Indique la base jurídica del tratamiento'),
        '## 5. Conservacion', '## 5. Conservación'),
        'Detalle durante cuanto tiempo', 'Detalle durante cuánto tiempo'),
        'y bajo que garantias.', 'y bajo qué garantías.'),
        'acceso, rectificacion, supresion, oposicion y', 'acceso, rectificación, supresión, oposición y'),
        '## 8. Cambios en esta politica', '## 8. Cambios en esta política'),
        'cualquier modificacion se publicara en esta misma pagina.', 'cualquier modificación se publicará en esta misma página.'),
        'consignar aqui la fecha', 'consignar aquí la fecha')
WHERE custom_privacy = FALSE;
