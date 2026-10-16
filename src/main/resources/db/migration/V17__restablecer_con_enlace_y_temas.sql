-- ============================================================================
-- 1) Plantilla RESET_PASSWORD: de "contraseña temporal" a "enlace".
--
-- El restablecimiento dejó de generar una contraseña temporal: el correo lleva
-- un enlace ([urlReset]) para que la persona cree la suya. La plantilla
-- guardada seguía mostrando [password], que llegaba vacío ("No llega la nueva
-- clave"). Solo se toca si aún contiene [password] (la antigua); se conserva el
-- diseño y se cambian los textos y el recuadro de la clave por el botón.
-- ============================================================================

UPDATE notification_template
SET body_template = replace(replace(replace(replace(replace(replace(replace(replace(replace(
      body_template,
      'Se ha restablecido tu contraseña de acceso a la <b>Mesa de Servicios</b>. Tu nueva <b>contraseña temporal</b> es:',
      'Recibimos una solicitud para restablecer tu contraseña de la <b>Mesa de Servicios</b>. Pulsa el botón para crear una <b>nueva contraseña</b>:'),
      '<p style="margin:0 0 6px;color:#6b7280;font-size:10px;text-transform:uppercase;letter-spacing:1px;font-weight:bold;">Contraseña temporal</p>',
      ''),
      '<p style="margin:0;font-family:''Courier New'',Consolas,monospace;font-size:20px;font-weight:bold;color:#062e86;letter-spacing:2px;word-break:break-all;">[password]</p>',
      '[password]'),
      '[password]',
      '<a href="[urlReset]" target="_blank" style="display:inline-block;padding:12px 26px;background:#062e86;color:#ffffff;font-size:15px;font-weight:bold;text-decoration:none;border-radius:8px;">Crear nueva contraseña</a><p style="margin:10px 0 0;color:#6b7280;font-size:11px;">El enlace vale 24 horas y solo se puede usar una vez.</p>'),
      'Por seguridad, al iniciar sesión deberás cambiar esta contraseña temporal por una personalizada.',
      'Elige una contraseña que no uses en otros sitios. Si no la pides tú, ignora este correo: tu contraseña actual sigue valiendo.'),
      'Ingresa al sistema con tu correo corporativo.</td>',
      'Pulsa <b>Crear nueva contraseña</b> en este correo.</td>'),
      'Usa la contraseña temporal que aparece arriba.',
      'Escribe tu nueva contraseña dos veces y guárdala.'),
      'Cambia la contraseña desde <b>Mi Perfil → Cambiar Contraseña</b>.',
      'Ingresa al sistema con tu correo corporativo y la nueva contraseña.'),
      'no compartir esta contraseña</b> con nadie y cambiarla inmediatamente después de iniciar sesión.',
      'no compartir tu contraseña</b> con nadie, ni siquiera con el equipo de TI.'),
    updated_at_template = now()
WHERE name_template = 'RESET_PASSWORD'
  AND body_template LIKE '%[password]%';

-- ============================================================================
-- 2) Temas de temporada nuevos: Fiestas de Quito, Fin de año y Carnaval.
--
-- Carnaval no tiene fecha fija (depende de la Pascua): rule_theme = 'CARNAVAL'
-- y el servidor calcula cada año del sábado al martes de carnaval; sus días y
-- meses guardados no se usan. Si dos temas coinciden (Navidad y Fiestas de
-- Quito a inicios de diciembre) gana el de menos días, el más concreto.
-- ============================================================================

ALTER TABLE seasonal_theme ADD COLUMN IF NOT EXISTS rule_theme VARCHAR(20);

INSERT INTO seasonal_theme (id_theme, code_theme, name_theme, start_month_theme, start_day_theme,
                            end_month_theme, end_day_theme, greeting_theme, rule_theme)
VALUES
  ('a0000000-0000-0000-0000-000000000003', 'FIESTAS_QUITO', 'Fiestas de Quito', 11, 28, 12, 6,
   '¡Viva Quito! Felices fiestas a nuestra carita de Dios.', NULL),
  ('a0000000-0000-0000-0000-000000000004', 'FIN_ANIO', 'Fin de año', 12, 27, 1, 2,
   '¡Feliz año nuevo! Gracias por un año de trabajo en equipo.', NULL),
  ('a0000000-0000-0000-0000-000000000005', 'CARNAVAL', 'Carnaval', 2, 1, 2, 1,
   '¡Feliz Carnaval! Disfruta del feriado con cuidado.', 'CARNAVAL')
ON CONFLICT (code_theme) DO NOTHING;

-- ============================================================================
-- 3) Sonidos por temporada: ámbito TEMPORADA, clave "<TEMA>:<urgencia>"
--    (p. ej. NAVIDAD:urgent). Mientras el tema está activo reemplaza al sonido
--    general de esa urgencia.
-- ============================================================================

ALTER TABLE notification_sound DROP CONSTRAINT IF EXISTS ck_notification_sound_scope;
ALTER TABLE notification_sound ADD CONSTRAINT ck_notification_sound_scope
    CHECK (scope_sound IN ('URGENCIA', 'TIPO', 'TEMPORADA'));
