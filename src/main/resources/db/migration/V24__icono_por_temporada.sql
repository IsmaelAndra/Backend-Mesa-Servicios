-- Icono propio de cada tema de temporada (cuadrado). Se usa como icono de la
-- pestaña y de la ventana de la aplicación mientras el tema está vigente; sin
-- él se usa el icono normal de la mesa.
ALTER TABLE seasonal_theme ADD COLUMN IF NOT EXISTS icon_url_theme VARCHAR(500);
