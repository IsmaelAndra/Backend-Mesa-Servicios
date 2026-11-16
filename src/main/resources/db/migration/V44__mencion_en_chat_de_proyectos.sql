-- ============================================================================
-- AVISO DE MENCIÓN EN EL CHAT DE PROYECTOS
-- ============================================================================
-- Escribir @Nombre en el chat de un proyecto avisa a esa persona (campana y
-- correo). Como todo correo del sistema, tiene su plantilla en Plantillas.
-- ============================================================================

INSERT INTO notification_template (name_template, type_template, subject_template, body_template,
                                   active_template, recipient_rule, recipient_email, icon_template, color_template)
VALUES
  ('PROYECTO_MENCION', 'PROYECTO', '[asunto]', '<div style="background:#f3f4f6;padding:32px 16px;"><div style="max-width:620px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;"><div style="background:#062e86;padding:24px 32px;text-align:center;">[logo]</div><div style="padding:28px 36px 12px;font-family:Arial,Helvetica,sans-serif;color:#374151;"><h2 style="color:#062e86;margin:0 0 18px;">Te mencionaron en un proyecto</h2><p>Estimado/a <b>[nombre]</b>:</p>[mensaje]<p style="margin:24px 0;text-align:center;"><a href="[urlTicket]" style="background:#062e86;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;">Abrir el chat</a></p></div><div style="background:#062e86;padding:20px 32px;text-align:center;font-family:Arial,Helvetica,sans-serif;"><p style="margin:0;color:#ffffff;font-weight:bold;font-size:14px;">Mesa de Servicios</p><p style="margin:6px 0 0;color:#c7d2fe;font-size:12px;">Servicio de soporte técnico · COAC Magisterio de Pichincha</p><p style="margin:6px 0 0;color:#aec0dd;font-size:11px;">© 2026 COAC Magisterio de Pichincha. Todos los derechos reservados.</p></div></div><p style="max-width:620px;margin:12px auto 0;text-align:center;font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#9ca3af;">Este correo fue generado automáticamente; por favor no responda a este mensaje.</p></div>', TRUE, 'DEFAULT', NULL, 'fa-solid fa-at', '#7C3AED')
ON CONFLICT (name_template) DO NOTHING;
