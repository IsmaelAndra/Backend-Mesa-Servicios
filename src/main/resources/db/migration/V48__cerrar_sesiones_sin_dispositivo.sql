-- Sesiones de antes de V47: salían en "Dispositivos" como "Dispositivo sin
-- identificar", una por cada inicio de sesión de las últimas semanas. Las que se
-- usan de verdad ya rotaron (y guardaron su dispositivo); las que quedan sin datos
-- no las usa nadie. Se cierran (sin borrar, igual que un logout).
UPDATE refresh_token
SET revoked = TRUE
WHERE revoked = FALSE
  AND dispositivo_refresh_token IS NULL;
