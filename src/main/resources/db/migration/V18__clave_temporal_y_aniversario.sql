-- ============================================================================
-- 1) "¿Olvidó su contraseña?" vuelve a enviar una contraseña temporal.
--
-- Queda PENDIENTE (hash + caducidad) sin reemplazar la actual: la de siempre
-- sigue valiendo hasta que se entra con la temporal. Así nadie puede dejar
-- fuera a otra persona solo con saber su correo.
-- ============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_password_expires_at TIMESTAMPTZ;

-- ============================================================================
-- 2) Plantilla RESET_PASSWORD: se deshace lo que hizo la V17 (enlace) y vuelve
--    a mostrar [password]. Solo si sigue con el botón que puso la V17.
-- ============================================================================

UPDATE notification_template
SET body_template = replace(replace(replace(replace(replace(replace(replace(replace(
      body_template,
      '<a href="[urlReset]" target="_blank" style="display:inline-block;padding:12px 26px;background:#062e86;color:#ffffff;font-size:15px;font-weight:bold;text-decoration:none;border-radius:8px;">Crear nueva contraseña</a><p style="margin:10px 0 0;color:#6b7280;font-size:11px;">El enlace vale 24 horas y solo se puede usar una vez.</p>',
      '<p style="margin:0 0 6px;color:#6b7280;font-size:10px;text-transform:uppercase;letter-spacing:1px;font-weight:bold;">Contraseña temporal</p><p style="margin:0;font-family:''Courier New'',Consolas,monospace;font-size:20px;font-weight:bold;color:#062e86;letter-spacing:2px;word-break:break-all;">[password]</p>'),
      'Recibimos una solicitud para restablecer tu contraseña de la <b>Mesa de Servicios</b>. Pulsa el botón para crear una <b>nueva contraseña</b>:',
      'Se ha generado una <b>contraseña temporal</b> para tu acceso a la <b>Mesa de Servicios</b>. Es esta:'),
      'Elige una contraseña que no uses en otros sitios. Si no la pides tú, ignora este correo: tu contraseña actual sigue valiendo.',
      'Vale 24 horas. Al entrar con ella se te pedirá cambiarla. Hasta entonces tu contraseña anterior sigue funcionando.'),
      'Pulsa <b>Crear nueva contraseña</b> en este correo.</td>',
      'Ingresa al sistema con tu correo corporativo.</td>'),
      'Escribe tu nueva contraseña dos veces y guárdala.',
      'Usa la contraseña temporal que aparece arriba.'),
      'Ingresa al sistema con tu correo corporativo y la nueva contraseña.',
      'Crea tu nueva contraseña cuando el sistema te la pida.'),
      'no compartir tu contraseña</b> con nadie, ni siquiera con el equipo de TI.',
      'no compartir esta contraseña</b> con nadie y cambiarla en cuanto entres.'),
      'Se ha restablecido tu contraseña de acceso a la <b>Mesa de Servicios</b>. Tu nueva <b>contraseña temporal</b> es:',
      'Se ha generado una <b>contraseña temporal</b> para tu acceso a la <b>Mesa de Servicios</b>. Es esta:'),
    updated_at_template = now()
WHERE name_template = 'RESET_PASSWORD'
  AND body_template LIKE '%[urlReset]%';

-- ============================================================================
-- 3) Temas de temporada: imagen propia para el encabezado del inicio y el
--    aniversario de la cooperativa (11 de noviembre de 1968). En el saludo,
--    {anios} se cambia por los años cumplidos.
-- ============================================================================

ALTER TABLE seasonal_theme ADD COLUMN IF NOT EXISTS dashboard_url_theme VARCHAR(500);

INSERT INTO seasonal_theme (id_theme, code_theme, name_theme, start_month_theme, start_day_theme,
                            end_month_theme, end_day_theme, greeting_theme)
VALUES
  ('a0000000-0000-0000-0000-000000000006', 'ANIVERSARIO', 'Aniversario de la cooperativa', 11, 11, 11, 11,
   '¡Feliz aniversario! {anios} años de la Cooperativa Magisterio de Pichincha, desde el 11 de noviembre de 1968.')
ON CONFLICT (code_theme) DO NOTHING;
