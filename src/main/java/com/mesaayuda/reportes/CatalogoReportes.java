package com.mesaayuda.reportes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tablas, columnas, medidas y filtros que se pueden usar en un reporte hecho
 * desde el modal. Es una lista CERRADA en el código, y esa es la garantía de
 * seguridad: la consulta se arma solo con estos trozos de SQL, nunca con texto
 * que venga del navegador. Para ofrecer una columna o una tabla nueva hay que
 * añadirla aquí. Desde la V32 es la única forma de crear reportes (ya no hay
 * reportes escritos en SQL).
 *
 * <p>Los tickets confidenciales salen siempre como "Ticket confidencial", sin
 * título, solicitante ni comentarios: un reporte se descarga y se comparte.
 */
public final class CatalogoReportes {

    /** Tipos de filtro: el frontend los pinta con el maestro correspondiente. */
    public enum TipoFiltro { AREA, ESTADO, PRIORIDAD, CATEGORIA, SUCURSAL, TIPO }

    /**
     * Columna de detalle. {@code agrupable}: se puede usar en "Agrupar por"
     * (las de texto corto: estado, área, agente...; no el título ni las fechas).
     */
    public record Columna(String clave, String etiqueta, String expresion, boolean agrupable) {
        public Columna(String clave, String etiqueta, String expresion) {
            this(clave, etiqueta, expresion, false);
        }
    }

    /** Total que se calcula por grupo (COUNT, AVG...). Solo tiene sentido al agrupar. */
    public record Medida(String clave, String etiqueta, String expresion) {
    }

    public record Filtro(String clave, String etiqueta, TipoFiltro tipo, String expresion) {
    }

    /**
     * Una tabla del constructor.
     *
     * @param desde        FROM con sus uniones
     * @param base         condición que siempre se aplica
     * @param columnaFecha columna por la que se filtra el periodo
     * @param columnaArea  área que ATIENDE (acota al jefe a sus áreas)
     */
    public record Tabla(String clave, String nombre, String descripcion, String icono, String desde, String base,
                        String columnaFecha, String columnaArea, String orden,
                        Map<String, Columna> columnas, Map<String, Medida> medidas, Map<String, Filtro> filtros) {
    }

    private static final String TITULO =
            "CASE WHEN t.confidencial_ticket THEN 'Ticket confidencial' ELSE t.title_ticket END";
    private static final String SOLICITANTE =
            "CASE WHEN t.confidencial_ticket THEN 'Confidencial' "
                    + "ELSE COALESCE(NULLIF(us.display_name, ''), us.corporate_email_user) END";
    private static final String AGENTE = "COALESCE(NULLIF(ag.display_name, ''), ag.corporate_email_user, 'Sin asignar')";
    private static final String MES =
            "to_char(%s AT TIME ZONE 'America/Guayaquil', 'YYYY-MM')";
    private static final String TERMINADO = "s.name_status IN ('RESUELTO', 'CERRADO')";

    /** Filtros comunes: todos sobre el ticket. */
    private static Map<String, Filtro> filtrosDeTicket() {
        return mapa(List.of(
                new Filtro("area", "Área que atiende", TipoFiltro.AREA, "t.area_destino_ticket"),
                new Filtro("estado", "Estado", TipoFiltro.ESTADO, "t.status_ticket"),
                new Filtro("prioridad", "Prioridad", TipoFiltro.PRIORIDAD, "t.priority_ticket"),
                new Filtro("categoria", "Categoría", TipoFiltro.CATEGORIA, "t.category_ticket"),
                new Filtro("sucursal", "Sucursal", TipoFiltro.SUCURSAL, "t.sucursal_id"),
                new Filtro("tipo", "Tipo de ticket", TipoFiltro.TIPO, "t.type_ticket")),
                Filtro::clave);
    }

    public static final Tabla TICKETS = new Tabla(
            "TICKETS", "Tickets", "Cada ticket con su estado, área, agente y cumplimiento de SLA.",
            "fa-solid fa-ticket",
            "ticket t "
                    + "LEFT JOIN users us ON us.id_user = t.user_ticket "
                    + "LEFT JOIN users ag ON ag.id_user = t.agent_ticket "
                    + "LEFT JOIN area ad ON ad.id_area = t.area_destino_ticket "
                    + "LEFT JOIN area asol ON asol.id_area = t.area_ticket "
                    + "LEFT JOIN status s ON s.id_status = t.status_ticket "
                    + "LEFT JOIN priority p ON p.id_priority = t.priority_ticket "
                    + "LEFT JOIN category c ON c.id_category = t.category_ticket "
                    + "LEFT JOIN ticket_type ty ON ty.id_ticket_type = t.type_ticket "
                    + "LEFT JOIN sucursal sc ON sc.id_sucursal = t.sucursal_id "
                    + "LEFT JOIN support_team tm ON tm.id_team = t.team_ticket "
                    + "LEFT JOIN ticket_sla ts ON ts.ticket_id = t.id_ticket "
                    + "LEFT JOIN sla_policy pol ON pol.id_sla_policy = ts.sla_policy_id",
            "t.deleted_ticket = FALSE", "t.created_at_ticket", "t.area_destino_ticket", "t.number_ticket DESC",
            mapa(List.of(
                    new Columna("numero", "Ticket", "t.number_ticket"),
                    new Columna("titulo", "Título", TITULO),
                    new Columna("estado", "Estado", "s.name_status", true),
                    new Columna("prioridad", "Prioridad", "p.name_priority", true),
                    new Columna("categoria", "Categoría", "c.name_category", true),
                    new Columna("tipo", "Tipo", "ty.name_ticket_type", true),
                    new Columna("area_atiende", "Área que atiende", "ad.name_area", true),
                    new Columna("area_solicitante", "Área del solicitante", "asol.name_area", true),
                    new Columna("sucursal", "Sucursal", "sc.name_sucursal", true),
                    new Columna("equipo", "Equipo", "tm.name_team", true),
                    new Columna("solicitante", "Solicitante", SOLICITANTE, true),
                    new Columna("agente", "Agente", AGENTE, true),
                    new Columna("mes", "Mes de creación", String.format(MES, "t.created_at_ticket"), true),
                    new Columna("creado", "Creado", "t.created_at_ticket"),
                    new Columna("cerrado", "Cierre", "t.closing_date_ticket"),
                    new Columna("horas_resolucion", "Horas de resolución",
                            "ROUND((EXTRACT(EPOCH FROM (t.closing_date_ticket - t.created_at_ticket)) / 3600.0)::numeric, 1)"),
                    new Columna("politica_sla", "Política SLA", "pol.name_sla_policy", true),
                    new Columna("vence_resolucion", "Vence resolución", "ts.resolution_due_at"),
                    new Columna("sla_respuesta", "SLA de respuesta",
                            "CASE WHEN ts.ticket_id IS NULL THEN 'Sin SLA' WHEN ts.response_breached "
                                    + "THEN 'Fuera de plazo' ELSE 'En plazo' END", true),
                    new Columna("sla_resolucion", "SLA de resolución",
                            "CASE WHEN ts.ticket_id IS NULL THEN 'Sin SLA' WHEN ts.resolution_breached "
                                    + "THEN 'Fuera de plazo' ELSE 'En plazo' END", true),
                    new Columna("confidencial", "Confidencial", "t.confidencial_ticket")),
                    Columna::clave),
            mapa(List.of(
                    new Medida("total", "Total de tickets", "COUNT(*)"),
                    new Medida("abiertos", "En curso", "COUNT(*) FILTER (WHERE NOT (" + TERMINADO + "))"),
                    new Medida("resueltos", "Resueltos o cerrados", "COUNT(*) FILTER (WHERE " + TERMINADO + ")"),
                    new Medida("sla_vencidos", "SLA de resolución vencido",
                            "COUNT(*) FILTER (WHERE ts.resolution_breached)"),
                    new Medida("sla_cumplido", "% SLA cumplido",
                            "ROUND(100.0 * COUNT(*) FILTER (WHERE ts.ticket_id IS NOT NULL AND NOT ts.resolution_breached) "
                                    + "/ NULLIF(COUNT(ts.ticket_id), 0), 1)"),
                    new Medida("horas_promedio", "Horas promedio de resolución",
                            "ROUND(AVG(EXTRACT(EPOCH FROM (t.closing_date_ticket - t.created_at_ticket)) / 3600.0)::numeric, 1)"),
                    // Subconsulta y no JOIN: un JOIN a las calificaciones duplicaría los conteos.
                    new Medida("satisfaccion", "Satisfacción promedio",
                            "ROUND(AVG((SELECT AVG(rr.overall_satisfaction) FROM ticket_rating rr "
                                    + "WHERE rr.ticket_id = t.id_ticket))::numeric, 2)")),
                    Medida::clave),
            filtrosDeTicket());

    public static final Tabla CALIFICACIONES = new Tabla(
            "CALIFICACIONES", "Calificaciones", "Lo que calificaron los solicitantes, criterio por criterio.",
            "fa-solid fa-star",
            "ticket_rating r "
                    + "JOIN ticket t ON t.id_ticket = r.ticket_id "
                    + "LEFT JOIN users us ON us.id_user = t.user_ticket "
                    + "LEFT JOIN users ag ON ag.id_user = t.agent_ticket "
                    + "LEFT JOIN area ad ON ad.id_area = t.area_destino_ticket "
                    + "LEFT JOIN category c ON c.id_category = t.category_ticket "
                    + "LEFT JOIN sucursal sc ON sc.id_sucursal = t.sucursal_id",
            "t.deleted_ticket = FALSE", "r.rated_at", "t.area_destino_ticket", "r.rated_at DESC",
            mapa(List.of(
                    new Columna("numero", "Ticket", "t.number_ticket"),
                    new Columna("titulo", "Título", TITULO),
                    new Columna("fecha", "Fecha", "r.rated_at"),
                    new Columna("solicitante", "Solicitante", SOLICITANTE, true),
                    new Columna("agente", "Agente", AGENTE, true),
                    new Columna("area_atiende", "Área que atiende", "ad.name_area", true),
                    new Columna("categoria", "Categoría", "c.name_category", true),
                    new Columna("sucursal", "Sucursal", "sc.name_sucursal", true),
                    new Columna("mes", "Mes", String.format(MES, "r.rated_at"), true),
                    new Columna("requisitos", "Requisitos", "r.requirements_met"),
                    new Columna("tiempo_respuesta", "Tiempo de respuesta", "r.response_time_rating"),
                    new Columna("calidad", "Calidad de la solución", "r.resolution_quality"),
                    new Columna("comunicacion", "Comunicación", "r.agent_communication"),
                    new Columna("satisfaccion", "Satisfacción", "r.overall_satisfaction"),
                    new Columna("comentarios", "Comentarios",
                            "CASE WHEN t.confidencial_ticket THEN '' ELSE COALESCE(r.comments, '') END")),
                    Columna::clave),
            mapa(List.of(
                    new Medida("total", "Calificaciones", "COUNT(*)"),
                    new Medida("satisfaccion", "Satisfacción media", "ROUND(AVG(r.overall_satisfaction)::numeric, 2)"),
                    new Medida("calidad", "Calidad media", "ROUND(AVG(r.resolution_quality)::numeric, 2)"),
                    new Medida("tiempo_respuesta", "Tiempo de respuesta medio",
                            "ROUND(AVG(r.response_time_rating)::numeric, 2)"),
                    new Medida("comunicacion", "Comunicación media", "ROUND(AVG(r.agent_communication)::numeric, 2)"),
                    new Medida("requisitos", "Requisitos medios", "ROUND(AVG(r.requirements_met)::numeric, 2)")),
                    Medida::clave),
            filtrosDeTicket());

    public static final Map<String, Tabla> TABLAS = mapa(List.of(TICKETS, CALIFICACIONES), Tabla::clave);

    private CatalogoReportes() {
    }

    private static <T> Map<String, T> mapa(List<T> lista, java.util.function.Function<T, String> clave) {
        Map<String, T> m = new LinkedHashMap<>();
        lista.forEach(x -> m.put(clave.apply(x), x));
        return java.util.Collections.unmodifiableMap(m);
    }
}
