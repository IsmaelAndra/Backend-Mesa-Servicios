-- Cuentas sin nombre (genéricas como superadmin@ o las que Microsoft Graph no
-- conoce): el nombre es la parte del correo antes de la @. Las actas y el chat
-- lo muestran en lugar del correo completo. Graph lo reemplaza si luego trae
-- el nombre real. Solo toca filas sin nombre.
UPDATE users
SET display_name = split_part(corporate_email_user, '@', 1)
WHERE (display_name IS NULL OR btrim(display_name) = '')
  AND corporate_email_user IS NOT NULL
  AND corporate_email_user <> '';
