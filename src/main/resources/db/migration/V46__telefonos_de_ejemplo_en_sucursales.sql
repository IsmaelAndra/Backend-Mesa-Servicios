-- Las sucursales de fábrica se sembraron con teléfonos de ejemplo ("02 2xxxxxxx").
-- No son teléfonos: la validación los rechaza y confundían al editar. Se vacían
-- solo los que llevan esas "x" de relleno; los escritos por un administrador no se tocan.
UPDATE sucursal
   SET phone_sucursal = NULL
 WHERE phone_sucursal ~* '^[0-9 ]*x+$';
