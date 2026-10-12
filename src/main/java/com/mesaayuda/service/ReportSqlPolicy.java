package com.mesaayuda.service;

import com.mesaayuda.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Qué consultas SQL se pueden guardar como "reporte personalizado".
 * <p>
 * Estas consultas las escribe un ADMIN y se ejecutan tal cual contra la base con
 * los permisos de la propia aplicación, así que sin más validación cualquiera
 * con ese rol podía sacar los hashes de contraseña de todo el tenant
 * ({@code SELECT password_user FROM users}), leer los secretos de Graph o
 * escribir en las tablas. La validación anterior solo miraba que la consulta
 * empezara por SELECT y que no contuviera palabras como INSERT o DROP, lo que
 * no impedía ninguna de esas lecturas.
 * <p>
 * Aquí se actúa por lo que el reporte <em>no</em> puede tocar:
 * <ul>
 *   <li>Solo se admiten las tablas del help desk ({@link #TABLAS_PERMITIDAS}).</li>
 *   <li>Las columnas de secreto se rechazan en cualquier parte de la consulta,
 *       así que da igual la tabla o el alias con el que se reach.</li>
 *   <li>No se admite {@code *} en la lista de columnas: con {@code t.*} la
 *       allowlist de columnas se esquivaría sola, porque el servidor expandiría
 *       la lista completa, incluido el hash de contraseña.</li>
 *   <li>Se prohíben los esquemas del sistema y las funciones que leen archivos
 *       o disparan trabajo pesado en el servidor.</li>
 *   <li>Solo se admiten los cuatro parámetros con los que ya cuentan los
 *       reportes sembrados.</li>
 * </ul>
 * No sustituye a una conexión de solo lectura; es la primera capa, y deja el
 * sistema con permisos de escritura solo para lo que realmente necesita escribir.
 */
@Component
public class ReportSqlPolicy {

    /** Tablas que un reporte puede leer. Cualquier otra se rechaza. */
private static final Set<String> TABLAS_PERMITIDAS = Set.of(
              "ticket", "ticket_sla", "ticket_audit", "ticket_rating", "ticket_type",
              "answer", "answer_read", "attach",
              "users", "role", "area", "category", "status", "priority",
              "support_team", "team_user", "sla_policy", "sla_rule", "sla_escalation",
              "monthly_evaluation", "task", "notification", "notification_template",
              "branding_config", "routing_config",
              // Sede fisica. Se puede consultar en solitario y unir a ticket y a
              // users para breakdowns por oficina. No aporta nada sensible: sus
              // columnas son codigo, nombre, direccion y telefono.
              "sucursal"
      );

    /**
     * Columnas que nunca deben salir de un reporte. Se comparan sin distinguir
     * mayúsculas y con límites de palabra, en cualquier punto de la consulta, de
     * modo que cambiar el alias o meter un subquery no las esquiva.
     */
    private static final List<String> COLUMNAS_BLOQUEADAS = List.of(
            "password_user", "password", "passwd", "pwd",
            "client_secret", "client_secret_encrypted",
            "token_hash", "api_key", "access_token", "secret",
            "must_change_password", "temporal_password_expires_at"
    );

    /**
     * Funciones que leen del sistema o que sirven para cargar el servidor.
     */
    private static final List<String> FUNCIONES_BLOQUEADAS = List.of(
            "pg_read_file", "pg_read_binary_file", "pg_ls_dir", "lo_import", "lo_export",
            "dblink", "pg_sleep", "pg_sleep_for", "pg_sleep_until",
            "pg_terminate_backend", "pg_cancel_backend", "set_config", "pg_reload_conf",
            // Introspección del servidor: current_setting permite leer cualquier
            // parámetro de configuración, y algunos de ellos revelan rutas del
            // sistema, versión o límites. Un reporte no necesita nada de eso.
            "current_setting", "set_config", "current_schemas", "current_database",
            "current_user", "session_user", "current_catalog", "version",
            "inet_server_addr", "inet_client_port", "inet_server_port",
            "pg_backend_pid", "pg_conf_file_time", "pg_tablespace_location",
            "pg_stat_get_activity", "pg_ls_logdir", "pg_read_file_lseek"
    );

    /** Tablas del catálogo del propio PostgreSQL, que están fuera del allowlist. */
    private static final List<String> ESQUEMAS_BLOQUEADOS = List.of(
            "pg_catalog.", "information_schema.", "pg_toast.", "pg_temp_", "sys.", "pg_authid"
    );

    /**
     * Parametros que un reporte puede usar. La lista es cerrada: un reporte que
     * invoque cualquier otro nombre es rechazado, y asi no se puede escribir un
     * SQL que espere un parametro que el backend nunca envia.
     */
    private static final List<String> PARAMETROS_PERMITIDOS =
            List.of("desde", "hasta", "tipoId", "usuarioId", "sucursalId");

    private static final int LONGITUD_MAXIMA = 8000;

    /**
     * Comentarios SQL. No hacen falta para un reporte y solo sirven para
     * escribir código que parece distinto de lo que es. Se comprueban por
     * separado de los literales porque un texto puede contener "--" sin que
     * sea un comentario.
     */
    private static final Pattern COMENTARIO = Pattern.compile("--|/\\*|\\*/");

    /** CTE: "WITH nombre AS ( ... )". Sus alias no son tablas reales. */
    private static final Pattern DEFINICION_CTE = Pattern.compile(
            "(?:\\bWITH\\b(?:\\s+RECURSIVE\\b)?|\\)\\s*,)\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+AS\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** Carácter de comilla simple, en decimal para no depender de escapes en el fuente. */
    private static final char APOSTROFIA = 39;

    /** Comilla doble: en PostgreSQL delimita un identificador, como un alias o columna. */
    private static final char COMILLA = 34;

    /** AFTER FROM, JOIN y también FROM dentro de un subquery. */
    private static final Pattern REFERENCIA_TABLA = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+([A-Za-z_][A-Za-z0-9_$]*)",
            Pattern.CASE_INSENSITIVE);

    /** FROM seguido de un subquery: se acepta, y sus FROM internos se analizan solos. */
    private static final Pattern SUBQUERY = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+\\(",
            Pattern.CASE_INSENSITIVE);

    /**
     * Parámetros nombrados. El {@code (?!:)} del final evita confundir el
     * operador de cast de PostgreSQL ({@code ::numeric}) con un parámetro, que
     * es lo que pasaba y rechazaba el reporte de SLA sembrado.
     */
    private static final Pattern PARAMETRO = Pattern.compile("(?<!:):([A-Za-z_][A-Za-z0-9_]*)(?!:)");

    /** El asterisco va en el grupo 3 para poder saber exactamente dónde está. */
    private static final Pattern SELECT_ESTRELLA = Pattern.compile(
            "(^|[\\s,(])(\\w+\\s*\\.\\s*)?(\\*)[\\s,)\\;]|(^|[\\s,(])(\\w+\\s*\\.\\s*)?(\\*)$",
            Pattern.CASE_INSENSITIVE);

    /** Palabras que cerran la lista de tablas, para saber dónde termina. */
    private static final List<String> CIERRAN_LISTA_DE_TABLAS = List.of(
            "where", "group", "having", "order", "limit", "offset", "union", "intersect", "except"
    );


    /**
     * Valida la consulta o lanza BadRequestException con un motivo legible. El
     * mensaje dice qué está mal, nunca qué quería: el administrador ya sabe
     * qué consulta ha escrito.
     */
    public void validar(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BadRequestException("La consulta SQL es obligatoria");
        }
        String limpia = sql.trim();
        if (limpia.length() > LONGITUD_MAXIMA) {
            throw new BadRequestException("La consulta es demasiado larga (máximo " + LONGITUD_MAXIMA + " caracteres)");
        }

        comprobarSoloLectura(limpia);
        comprobarSinComentarios(limpia);
        comprobarSinPuntoYComaInterno(limpia);
        comprobarSinFuncionesPeligrosas(limpia);
        comprobarSinColumnasSecretas(limpia);
        comprobarParametros(limpia);
        comprobarSinEstrellas(limpia);
        comprobarTablas(limpia);
    }

    private void comprobarSoloLectura(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new BadRequestException("La consulta debe comenzar con SELECT o WITH (solo lectura)");
        }
for (String bad : List.of("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
                "CREATE", "GRANT", "REVOKE", "COPY", "EXECUTE", "CALL", "MERGE",
                "DO", "VACUUM", "ANALYZE", "REINDEX", "COMMENT", "LOCK", "REFRESH",
                // INTO y OVERWRITE convierten un SELECT en una sentencia que
                // ESCRIBE: en PostgreSQL "SELECT a FROM t INTO tabla_nueva" crea
                // la tabla dentro de la misma sentencia, sin punto y coma y sin
                // ninguna de las palabras anteriores. Con la conexion de la
                // aplicacion, que si tiene permisos de escritura, eso era DDL
                // efectivo desde un endpoint descrito como de solo lectura.
                "INTO", "OVERWRITE")) {
            // Se exige que aparezca como palabra suelta, no dentro de un literal
            // como 'NO SE USA UPDATE'. Por eso se mira el contexto de la coincidencia.
            if (esPalabraClave(upper, bad)) {
                throw new BadRequestException("La consulta contiene una instrucción no permitida: " + bad);
            }
        }
    }

    /**
     * Los comentarios se rechazan. En PostgreSQL un comentario actua como un
     * espacio en blanco, de modo que partir el nombre de una columna sensible
     * con un comentario entre medias no produce el nombre real: la consulta
     * llega al servidor como dos identificadores sueltos y falla. Aun asi se
     * rechaza aqui, porque es preferible un mensaje claro a aceptar una
     * consulta que despues el servidor rechaza con un error de sintaxis.
     */
    private void comprobarSinComentarios(String sql) {
        if (COMENTARIO.matcher(sql).find()) {
            throw new BadRequestException("La consulta no puede contener comentarios SQL");
        }
    }

    private void comprobarSinPuntoYComaInterno(String sql) {
        String sinCierre = sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql;
        if (sinCierre.contains(";")) {
            throw new BadRequestException("La consulta no puede contener varias sentencias");
        }
    }

    private void comprobarSinFuncionesPeligrosas(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        for (String fx : FUNCIONES_BLOQUEADAS) {
            if (lower.contains(fx + "(") || lower.contains(fx)) {
                throw new BadRequestException("La consulta usa una función no permitida: " + fx);
            }
        }
        for (String esquema : ESQUEMAS_BLOQUEADOS) {
            if (lower.contains(esquema)) {
                throw new BadRequestException("La consulta no puede leer tablas del sistema (" + esquema + ")");
            }
        }
    }

    private void comprobarSinColumnasSecretas(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        for (String col : COLUMNAS_BLOQUEADAS) {
            Matcher m = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(col) + "(?![a-z0-9_])")
                    .matcher(lower);
            if (m.find()) {
                throw new BadRequestException(
                        "La consulta no puede usar la columna '" + col + "': contiene datos sensibles");
            }
        }
    }

    private void comprobarParametros(String sql) {
        Matcher m = PARAMETRO.matcher(sinLiterales(sql));
        while (m.find()) {
            String nombre = m.group(1);
            boolean permitido = PARAMETROS_PERMITIDOS.stream()
                    .anyMatch(p -> p.equalsIgnoreCase(nombre));
            if (!permitido) {
                throw new BadRequestException("Parámetro no permitido: :" + nombre
                        + ". Disponibles: :desde, :hasta, :tipoId, :usuarioId");
            }
        }
    }

    /**
     * Devuelve la consulta con el contenido de los literales entre comillas
     * sustituido por espacios. Hace falta porque muchos reportes reales usan
     * formatos de fecha: {@code TO_CHAR(fecha, 'YYYY-MM-DD HH24:MI')} contiene
     * un dos puntos que, leído sin mirar las comillas, parecía un parámetro.
     */
    private String sinLiterales(String sql) {
        StringBuilder salida = new StringBuilder(sql.length());
        boolean enLiteral = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == APOSTROFIA) {
                enLiteral = !enLiteral;
                salida.append(APOSTROFIA);
                continue;
            }
            salida.append(enLiteral ? ' ' : c);
        }
        return salida.toString();
    }

    private void comprobarSinEstrellas(String sql) {
        // COUNT(*) es legítimo: el * es un argumento de función. Lo que no se
        // admite es proyectar columnas sin nombrarlas, porque el servidor las
        // resolvería todas, incluido el hash de contraseña. Por eso aquí no se
        // reescribe la consulta, sino que se comprueba, para cada asterisco, si
        // lo que tiene justo delante es un paréntesis de apertura.
        Matcher m = SELECT_ESTRELLA.matcher(sinLiterales(sql));
        while (m.find()) {
            int posEstrella = m.start(3) >= 0 ? m.start(3) : m.start(6);
            if (posEstrella < 0 || !esArgumentoDeFuncion(sql, posEstrella)) {
                throw new BadRequestException(
                        "La consulta debe nombrar las columnas una a una: no se admite '*' "
                                + "(expandiría columnas que no están autorizadas)");
            }
        }
    }

    /** ¿El carácter anterior al asterisco, saltando espacios, es un paréntesis? */
    private boolean esArgumentoDeFuncion(String sql, int posEstrella) {
        for (int i = posEstrella - 1; i >= 0; i--) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '(';
        }
        return false;
    }

    private void comprobarTablas(String sql) {
        // Los alias de un CTE no son tablas: "WITH ultimos AS (...) SELECT ...
        // FROM ultimos" debe aceptarse, aunque "ultimos" no esté en la
        // allowlist. Se recogen y se excluyen del análisis.
        Set<String> aliasCte = new LinkedHashSet<>();
        Matcher mc = DEFINICION_CTE.matcher(sql);
        while (mc.find()) {
            aliasCte.add(mc.group(1).toLowerCase(Locale.ROOT));
        }

        Set<String> referencias = new LinkedHashSet<>();
        Matcher m = REFERENCIA_TABLA.matcher(sql);
        while (m.find()) {
            String tabla = m.group(1).toLowerCase(Locale.ROOT);
            if (!aliasCte.contains(tabla)) {
                referencias.add(tabla);
            }
        }
        if (referencias.isEmpty()) {
            throw new BadRequestException("No se ha encontrado ninguna tabla en la consulta");
        }
        for (String tabla : referencias) {
            if (!TABLAS_PERMITIDAS.contains(tabla)) {
                throw new BadRequestException("La tabla '" + tabla + "' no está autorizada para reportes. "
                        + "Tablas permitidas: " + String.join(", ", TABLAS_PERMITIDAS.stream().sorted().toList()));
            }
        }
        if (tieneJoinPorComa(sql)) {
            throw new BadRequestException(
                    "No se admiten joins con coma: usa JOIN ... ON ... de forma explícita");
        }
    }

    /**
     * Detecta el join viejo {@code FROM ticket t, users u}. Se recorre el texto
     * contando paréntesis porque una coma dentro de COUNT(DISTINCT a, b) o de
     * un subquery no es un join, y una búsqueda con una expresión regular las
     * confundía y rechazaba reportes que funcionaban.
     */
    private boolean tieneJoinPorComa(String sql) {
        String bajo = sql.toLowerCase(Locale.ROOT);
        int profundidad = 0;
        boolean enLiteral = false;
        boolean esperandoCierre = false;
        int largo = bajo.length();

        for (int i = 0; i < largo; i++) {
            char c = bajo.charAt(i);

            if (enLiteral) {
                if (c == APOSTROFIA) {
                    enLiteral = false;
                }
                continue;
            }
            if (c == APOSTROFIA) {
                enLiteral = true;
                continue;
            }
            if (c == '(') {
                profundidad++;
                continue;
            }
            if (c == ')') {
                profundidad--;
                continue;
            }
            if (profundidad != 0) {
                continue;
            }
            if (c == ',' && esperandoCierre) {
                return true;
            }
            if (c == ';' && esperandoCierre) {
                esperandoCierre = false;
                continue;
            }

            if (esperandoCierre) {
                if (empiezaPalabra(bajo, i, CIERRAN_LISTA_DE_TABLAS)) {
                    esperandoCierre = false;
                }
            } else if (empiezaPalabra(bajo, i, List.of("from", "join"))) {
                esperandoCierre = true;
            }
        }
        return false;
    }

    /**
     * ¿En la posición i empieza alguna de las palabras dadas como token suelto?
     * Se compara sin distinguir mayúsculas y exigiendo que lo que venga detrás
     * sea un espacio o el final, para que "frontend" no cuente como "from".
     */
    private boolean empiezaPalabra(String bajo, int i, List<String> palabras) {
        for (String palabra : palabras) {
            if (!bajo.startsWith(palabra, i)) {
                continue;
            }
            int fin = i + palabra.length();
            boolean limite = fin >= bajo.length()
                    || !Character.isLetterOrDigit(bajo.charAt(fin));
            if (limite) {
                return true;
            }
        }
        return false;
    }

    /**
     * Una palabra clave solo cuenta si aparece como token suelto, no dentro de un
     * literal entre comillas. Sin esto, un reporte con un título como
     * 'Plan de actualización' se rechazaría sin motivo.
     */
    private boolean esPalabraClave(String sqlMayusculas, String palabra) {
        Matcher m = Pattern.compile("(?<![A-Z0-9_])" + Pattern.quote(palabra) + "(?![A-Z0-9_])")
                .matcher(sqlMayusculas);
        while (m.find()) {
            if (!estaDentroDeLiteral(sqlMayusculas, m.start())) {
                return true;
            }
        }
        return false;
    }

private boolean estaDentroDeLiteral(String sql, int posicion) {
          // Se recorre el texto hasta la posicion cacheando en que tipo de literal
          // estamos, en vez de contar comillas. Contarlas no funciona: dentro de
          // un literal de texto una comilla se escapa duplicandola ('lo''hijo') y
          // un identificador entrecomillado con dobles admite las mismas comillas
          // dobles escapadas.
          //
          // Antes solo seellia el literal de comilla simple, de modo que un alias
          // como AS "Revision UPDATE" se contaba como codigo y hacia rechazar un
          // reporte por una palabra que era el nombre de una columna.
          boolean enTexto = false;
          boolean enIdentificador = false;
          for (int i = 0; i < posicion; i++) {
              char c = sql.charAt(i);
              if (enTexto) {
                  if (c == APOSTROFIA) {
                      // Doble comilla dentro de un literal: es una comilla escaped,
                      // no el cierre.
                      if (i + 1 < sql.length() && sql.charAt(i + 1) == APOSTROFIA) {
                          i++;
                      } else {
                          enTexto = false;
                      }
                  }
              } else if (enIdentificador) {
                  if (c == COMILLA) {
                      if (i + 1 < sql.length() && sql.charAt(i + 1) == COMILLA) {
                          i++;
                      } else {
                          enIdentificador = false;
                      }
                  }
              } else if (c == APOSTROFIA) {
                  enTexto = true;
              } else if (c == COMILLA) {
                  enIdentificador = true;
              }
          }
          return enTexto || enIdentificador;
      }

    /**
     * Añade un cast explícito a los parámetros con nombre antes de ejecutar.
     * <p>
     * Sin esto, un reporte sin rango de fechas fallaba siempre con "no se pudo
     * determinar el tipo del parámetro $1". La causa es que el patrón que usan
     * todos los reportes, {@code (:desde IS NULL OR col >= :desde)}, deja a
     * PostgreSQL sin forma de inferir el tipo de un parámetro que llega nulo: en
     * la comparación con la columna sí lo deduce, pero el {@code IS NULL} de la
     * misma sentencia lo deja ambiguo. Se comprobó contra el servidor: con
     * {@code ::timestamptz} la sentencia se prepara y se ejecuta con NULL sin
     * problema.
     * <p>
     * Se hace aquí y no editando las consultas ya guardadas, para que el arreglo
     * alcance también a los reportes que un administrador haya creado a mano.
     */
    public String conCastosExplicitos(String sql) {
        StringBuilder salida = new StringBuilder(sql.length() + 64);
        boolean enLiteral = false;
        int i = 0;
        int largo = sql.length();
        while (i < largo) {
            char c = sql.charAt(i);
            if (c == APOSTROFIA) {
                enLiteral = !enLiteral;
                salida.append(c);
                i++;
                continue;
            }
            if (!enLiteral && c == ':' && i + 1 < largo && sql.charAt(i + 1) == ':') {
                // Cast propio de PostgreSQL: se deja tal cual.
                salida.append("::");
                i += 2;
                continue;
            }
            if (!enLiteral && c == ':' && i + 1 < largo
                    && (Character.isLetter(sql.charAt(i + 1)) || sql.charAt(i + 1) == '_')) {
                int fin = i + 1;
                while (fin < largo
                        && (Character.isLetterOrDigit(sql.charAt(fin)) || sql.charAt(fin) == '_')) {
                    fin++;
                }
                String nombre = sql.substring(i + 1, fin);
                String cast = castDe(nombre);
                if (cast != null) {
                    salida.append(cast);
                } else {
                    salida.append(sql, i, fin);
                }
                i = fin;
                continue;
            }
            salida.append(c);
            i++;
        }
        return salida.toString();
    }

    private String castDe(String nombre) {
        for (String permitido : PARAMETROS_PERMITIDOS) {
            if (permitido.equalsIgnoreCase(nombre)) {
                return (permitido.equalsIgnoreCase("desde") || permitido.equalsIgnoreCase("hasta"))
                        ? "CAST(:" + permitido + " AS TIMESTAMPTZ)"
                        // Los ids (tipo, usuario, sede) son UUID desde V3__ids_uuid.sql.
                        : "CAST(:" + permitido + " AS UUID)";
            }
        }
        return null;
    }

    /** Visible para las pruebas y para los mensajes de la interfaz. */
    public Set<String> tablasPermitidas() {
        return TABLAS_PERMITIDAS;
    }
}
