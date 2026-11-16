package com.mesaayuda.reportes;

import com.mesaayuda.exception.BadRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arma la consulta de un reporte del modal a partir de su definición. Solo usa
 * los trozos de {@link CatalogoReportes}; los valores elegidos van siempre como
 * parámetros. Sin estado: se puede probar sin base de datos.
 */
public final class ConstructorConsulta {

    public static final int MAX_COLUMNAS = 25;
    /** Como mucho tres niveles al agrupar (área → agente → mes ya es bastante). */
    public static final int MAX_GRUPOS = 3;
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    /**
     * Lo que se guarda de un reporte: tabla, columnas en orden, filtros
     * (clave → uuid) y, si es un resumen, las medidas. Con medidas, las columnas
     * son los grupos ("Agrupar por") y cada fila es un grupo con sus totales.
     */
    public record Definicion(String tabla, List<String> columnas, Map<String, String> filtros, List<String> medidas) {
        public Definicion(String tabla, List<String> columnas, Map<String, String> filtros) {
            this(tabla, columnas, filtros, null);
        }

        public boolean esResumen() {
            return medidas != null && !medidas.isEmpty();
        }
    }

    public record Consulta(String sql, MapSqlParameterSource parametros, List<String> encabezados) {
    }

    private ConstructorConsulta() {
    }

    /** Comprueba la definición contra el catálogo. Lanza 400 con un motivo claro. */
    public static CatalogoReportes.Tabla validar(Definicion d) {
        if (d == null || d.tabla() == null) {
            throw new BadRequestException("Elija la tabla del reporte");
        }
        CatalogoReportes.Tabla tabla = CatalogoReportes.TABLAS.get(d.tabla());
        if (tabla == null) {
            throw new BadRequestException("Tabla de reporte no válida: " + d.tabla());
        }
        if (d.columnas() == null || d.columnas().isEmpty()) {
            throw new BadRequestException("Elija al menos una columna");
        }
        if (d.columnas().size() > MAX_COLUMNAS) {
            throw new BadRequestException("Como máximo " + MAX_COLUMNAS + " columnas");
        }
        for (String c : d.columnas()) {
            if (!tabla.columnas().containsKey(c)) {
                throw new BadRequestException("Columna no válida para " + tabla.nombre() + ": " + c);
            }
        }
        if (d.esResumen()) {
            if (d.columnas().size() > MAX_GRUPOS) {
                throw new BadRequestException("Se puede agrupar por " + MAX_GRUPOS + " columnas como máximo");
            }
            for (String c : d.columnas()) {
                if (!tabla.columnas().get(c).agrupable()) {
                    throw new BadRequestException("No se puede agrupar por " + tabla.columnas().get(c).etiqueta());
                }
            }
            for (String m : d.medidas()) {
                if (!tabla.medidas().containsKey(m)) {
                    throw new BadRequestException("Total no válido para " + tabla.nombre() + ": " + m);
                }
            }
        }
        if (d.filtros() != null) {
            for (Map.Entry<String, String> f : d.filtros().entrySet()) {
                if (!tabla.filtros().containsKey(f.getKey())) {
                    throw new BadRequestException("Filtro no válido para " + tabla.nombre() + ": " + f.getKey());
                }
                if (f.getValue() != null && !f.getValue().isBlank()) {
                    uuid(f.getValue(), f.getKey());
                }
            }
        }
        return tabla;
    }

    /**
     * @param ambito áreas a las que se limita quien lo ejecuta (jefe); null = sin límite.
     * @param limite filas como máximo (vista previa); null = las del límite general.
     */
    public static Consulta construir(Definicion d, LocalDate desde, LocalDate hasta, Set<UUID> ambito, Integer limite) {
        CatalogoReportes.Tabla tabla = validar(d);
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<String> encabezados = new ArrayList<>();
        List<String> select = new ArrayList<>();
        for (String clave : d.columnas()) {
            CatalogoReportes.Columna col = tabla.columnas().get(clave);
            // La etiqueta sale del catálogo (no del navegador): se puede usar de alias.
            select.add(col.expresion() + " AS \"" + col.etiqueta() + "\"");
            encabezados.add(col.etiqueta());
        }
        if (d.esResumen()) {
            for (String clave : d.medidas()) {
                CatalogoReportes.Medida m = tabla.medidas().get(clave);
                select.add(m.expresion() + " AS \"" + m.etiqueta() + "\"");
                encabezados.add(m.etiqueta());
            }
        }
        List<String> where = new ArrayList<>();
        where.add(tabla.base());
        if (desde != null) {
            where.add(tabla.columnaFecha() + " >= :desde");
            p.addValue("desde", desde.atStartOfDay(ZONA).toOffsetDateTime(), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (hasta != null) {
            where.add(tabla.columnaFecha() + " < :hasta");
            p.addValue("hasta", hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime(), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (d.filtros() != null) {
            int i = 0;
            for (Map.Entry<String, String> f : d.filtros().entrySet()) {
                if (f.getValue() == null || f.getValue().isBlank()) {
                    continue;
                }
                String nombre = "f" + (i++);
                where.add(tabla.filtros().get(f.getKey()).expresion() + " = :" + nombre);
                p.addValue(nombre, uuid(f.getValue(), f.getKey()), Types.OTHER);
            }
        }
        if (ambito != null) {
            if (ambito.isEmpty()) {
                where.add("FALSE");
            } else {
                // El jefe solo ve lo que atienden sus áreas, elija el filtro que elija.
                where.add(tabla.columnaArea() + " IN (:ambito)");
                p.addValue("ambito", new ArrayList<>(ambito));
            }
        }
        // Resumen: GROUP BY por posición de los grupos y orden por ellos mismos.
        String agrupar = "";
        String orden = tabla.orden();
        if (d.esResumen()) {
            List<String> posiciones = new ArrayList<>();
            for (int i = 1; i <= d.columnas().size(); i++) {
                posiciones.add(String.valueOf(i));
            }
            agrupar = " GROUP BY " + String.join(", ", posiciones);
            orden = String.join(", ", posiciones);
        }
        String sql = "SELECT " + String.join(", ", select)
                + " FROM " + tabla.desde()
                + " WHERE " + String.join(" AND ", where)
                + agrupar
                + " ORDER BY " + orden
                + (limite == null ? "" : " LIMIT " + Math.max(1, limite));
        return new Consulta(sql, p, encabezados);
    }

    private static UUID uuid(String valor, String filtro) {
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Valor no válido para el filtro " + filtro);
        }
    }
}
