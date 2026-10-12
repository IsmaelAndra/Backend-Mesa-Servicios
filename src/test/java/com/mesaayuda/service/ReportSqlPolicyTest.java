package com.mesaayuda.service;

import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que un ADMIN puede meter como "reporte personalizado". Estas consultas se
 * ejecutan contra la base con los permisos de la aplicación, así que la
 * validación es el único límite entre un rol administrativo y los datos de todo
 * el tenant, incluidos los hashes de contraseña.
 */
class ReportSqlPolicyTest {

    private final ReportSqlPolicy policy = new ReportSqlPolicy();

    // ============================ LOS REPORTES REALES SIGUEN VALIENDO ============================

    @Test
    @DisplayName("acepta el reporte de tickets con sus siete joins")
    void aceptaReporteTickets() {
        assertThatCode(() -> policy.validar("""
                SELECT t.id_ticket AS "Ticket",
                       t.title_ticket AS "Título",
                       us.corporate_email_user AS "Solicitante",
                       ag.corporate_email_user AS "Agente",
                       a.name_area AS "Área",
                       TO_CHAR(t.created_at_ticket, 'YYYY-MM-DD') AS "Creado"
                FROM ticket t
                LEFT JOIN users us ON us.id_user = t.user_ticket
                LEFT JOIN users ag ON ag.id_user = t.agent_ticket
                LEFT JOIN area a ON a.id_area = t.area_ticket
                WHERE (:desde IS NULL OR t.created_at_ticket >= :desde)
                  AND (:hasta IS NULL OR t.created_at_ticket < :hasta)
                  AND (:tipoId IS NULL OR t.type_ticket = :tipoId)
                ORDER BY t.id_ticket DESC
                """)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acepta el reporte de SLA con su CASE y sus agregados")
    void aceptaReporteSla() {
        assertThatCode(() -> policy.validar("""
                SELECT t.id_ticket AS "Ticket",
                       pol.name_sla_policy AS "Política",
                       CASE WHEN ts.response_breached THEN 'Fuera de plazo' ELSE 'En plazo' END AS "Respuesta",
                       COUNT(DISTINCT t.id_ticket) FILTER (WHERE s.name_status IN ('NUEVO','ASIGNADO')) AS "Pendientes",
                       ROUND(AVG(r.overall_satisfaction)::numeric, 2) AS "Satisfacción"
                FROM ticket_sla ts
                JOIN ticket t ON t.id_ticket = ts.ticket_id
                LEFT JOIN status s ON s.id_status = t.status_ticket
                LEFT JOIN ticket_rating r ON r.ticket_id = t.id_ticket
                LEFT JOIN sla_policy pol ON pol.id_sla_policy = ts.sla_policy_id
                WHERE (:desde IS NULL OR ts.created_at >= :desde)
                GROUP BY pol.name_sla_policy, ts.response_breached
                """)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acepta las cuatro consultas sembradas tal cual")
    void aceptaLasSembradas() {
        List<String> consultas = List.of(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t ORDER BY t.id_ticket",
                "SELECT pol.response_time_minutes AS \"Resp\" FROM ticket_sla ts "
                        + "LEFT JOIN sla_policy pol ON pol.id_sla_policy = ts.sla_policy_id",
                "SELECT COALESCE(ag.corporate_email_user, 'Sin agente') AS \"Agente\", COUNT(DISTINCT t.id_ticket) AS \"Tickets\" "
                        + "FROM ticket t LEFT JOIN users ag ON ag.id_user = t.agent_ticket GROUP BY ag.corporate_email_user",
                "SELECT r.overall_satisfaction AS \"Satisfacción\" FROM ticket_rating r "
                        + "JOIN ticket t ON t.id_ticket = r.ticket_id");

        for (String sql : consultas) {
            assertThatCode(() -> policy.validar(sql))
                    .as("debía aceptar: " + sql)
                    .doesNotThrowAnyException();
        }
    }

    // ============================ LO QUE NO DEBE PASAR ============================

    @Test
    @DisplayName("rechaza sacar los hashes de contraseña de todos los usuarios")
    void rechazaRoboDeHashes() {
        assertThatThrownBy(() -> policy.validar("SELECT password_user AS \"Clave\" FROM users"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password_user");
    }

    @Test
    @DisplayName("rechaza el hash aunque se esconda tras un alias y un subquery")
    void rechazaHashConAlias() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT x.c FROM (SELECT password_user AS c FROM users) x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password_user");
    }

    @Test
    @DisplayName("rechaza SELECT * porque el servidor expandiría el hash")
    void rechazaEstrella() {
        assertThatThrownBy(() -> policy.validar("SELECT * FROM users"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("*");
    }

    @Test
    @DisplayName("rechaza alias.* por la misma razón")
    void rechazaEstrellaConAlias() {
        assertThatThrownBy(() -> policy.validar("SELECT u.* FROM users u"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("admite COUNT(*) porque es una función, no una proyección")
    void admiteCountEstrella() {
        assertThatCode(() -> policy.validar("SELECT COUNT(*) AS \"Total\" FROM ticket"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza una tabla que no está en la allowlist")
    void rechazaTablaNoAutorizada() {
        assertThatThrownBy(() -> policy.validar("SELECT id_user FROM refresh_token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("refresh_token");
    }

    @Test
    @DisplayName("rechaza el catálogo del sistema")
    void rechazaPgCatalog() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT rolname AS \"Rol\" FROM pg_catalog.pg_roles"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza information_schema")
    void rechazaInformationSchema() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT table_name AS \"T\" FROM information_schema.tables"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza leer archivos del servidor")
    void rechazaPgReadFile() {
        assertThatThrownBy(() -> policy.validar("SELECT pg_read_file('/etc/passwd') AS \"F\" FROM ticket"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza el cliente secreto de Graph cifrado")
    void rechazaSecretoGraph() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT client_secret_encrypted AS \"S\" FROM microsoft_graph_config"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza escribir, aunque venga dentro de un WITH")
    void rechazaEscrituraEnWith() {
        assertThatThrownBy(() -> policy.validar(
                "WITH x AS (DELETE FROM users RETURNING id_user) SELECT id_user FROM x"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza apilar dos sentencias")
    void rechazaDosSentencias() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT id_ticket AS \"T\" FROM ticket; DROP TABLE users"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza un parámetro no previsto")
    void rechazaParametroDesconocido() {
        assertThatThrownBy(() -> policy.validar("SELECT id_ticket AS \"T\" FROM ticket WHERE id_ticket = :secreto"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("secreto");
    }

    @Test
    @DisplayName("rechaza un join con coma, que el análisis no puede seguir")
    void rechazaJoinConComa() {
        assertThatThrownBy(() -> policy.validar("SELECT t.id_ticket FROM ticket t, users u"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("coma");
    }

    // ============================ FALSOS POSITIVOS ============================

    @Test
    @DisplayName("no rechaza un texto que mencione UPDATE dentro de un literal")
    void toleraPalabraClaveEnLiteral() {
        assertThatCode(() -> policy.validar(
                "SELECT t.title_ticket AS \"Titulo\" FROM ticket t "
                        + "WHERE t.title_ticket = 'Plan de actualizacion del sistema'"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acepta un punto y coma final, que no es una segunda sentencia")
    void toleraPuntoYComaFinal() {
        assertThatCode(() -> policy.validar("SELECT id_ticket AS \"T\" FROM ticket;"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acepta un subquery anidado sobre tablas permitidas")
    void aceptaSubqueryAnidado() {
        assertThatCode(() -> policy.validar("""
                SELECT t.title_ticket AS "Título"
                FROM ticket t
                WHERE t.user_ticket IN (SELECT u.id_user FROM users u WHERE u.status_user = 'ACTIVE')
                """)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no confunde el dos puntos de un formato de fecha con un parámetro")
    void toleraFormatoDeFechaEnLiteral() {
        // TO_CHAR(..., 'YYYY-MM-DD HH24:MI') tiene un dos puntos y una palabra
        // detrás; sin mirar las comillas parecía el parámetro :MI y el reporte
        // de tickets sembrado quedaba rechazado.
        assertThatCode(() -> policy.validar("""
                SELECT t.id_ticket AS "Ticket",
                       TO_CHAR(t.created_at_ticket, 'YYYY-MM-DD HH24:MI') AS "Creado",
                       TO_CHAR(t.closing_date_ticket, 'YYYY-MM-DD') AS "Cierre"
                FROM ticket t
                WHERE (:desde IS NULL OR t.created_at_ticket >= :desde)
                """)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("añade cast a los parámetros para que un NULL no rompa la inferencia de PostgreSQL")
    void anadeCastos() {
        String sql = "SELECT id_ticket AS \"T\" FROM ticket "
                + "WHERE (:desde IS NULL OR created_at_ticket >= :desde) "
                + "AND (:hasta IS NULL OR created_at_ticket < :hasta) "
                + "AND (:tipoId IS NULL OR type_ticket = :tipoId)";

        String preparado = policy.conCastosExplicitos(sql);

        assertThat(preparado)
                .contains("CAST(:desde AS TIMESTAMPTZ)")
                .contains("CAST(:hasta AS TIMESTAMPTZ)")
                .contains("CAST(:tipoId AS UUID)");
    }

    @Test
    @DisplayName("no toca los dos puntos de un cast de PostgreSQL")
    void noRompelaCastDePostgres() {
        String preparado = policy.conCastosExplicitos(
                "SELECT ROUND(AVG(x)::numeric, 2) AS \"R\" FROM ticket");

        assertThat(preparado).contains("::numeric");
    }

    @Test
    @DisplayName("no toca el contenido de los literales")
    void noTocaLiterales() {
        String preparado = policy.conCastosExplicitos(
                "SELECT TO_CHAR(fecha, 'YYYY-MM-DD HH24:MI') AS \"F\" FROM ticket");

        assertThat(preparado).contains("'YYYY-MM-DD HH24:MI'");
    }

    @Test
    @DisplayName("rechaza las funciones de introspección del servidor")
    void rechazaIntrospeccion() {
        // current_setting permite leer cualquier parámetro de configuración, y
        // algunos revelan rutas del sistema o límites del servidor.
        assertThatThrownBy(() -> policy.validar(
                "SELECT current_setting('data_directory') AS \"D\" FROM ticket"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rechaza los comentarios SQL")
    void rechazaComentarios() {
        // En PostgreSQL el comentario actúa como espacio, así que partir un
        // nombre de columna no funciona; pero se rechaza antes de que la
        // consulta llegue al servidor, con un mensaje claro.
        assertThatThrownBy(() -> policy.validar(
                "SELECT id_ticket AS \"T\" FROM ticket -- solo los tickets"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("comentarios");
    }

    @Test
    @DisplayName("acepta un CTE de solo lectura: su alias no es una tabla real")
    void aceptaCte() {
        // El mensaje de error anterior sugería que se podía empezar por WITH,
        // pero luego el alias del CTE se rechazaba como tabla no autorizada.
        assertThatCode(() -> policy.validar("""
                WITH recientes AS (
                    SELECT t.id_ticket AS id, t.title_ticket AS titulo
                    FROM ticket t
                    WHERE t.status_ticket.name_status <> 'CERRADO'
                )
                SELECT r.id AS "Ticket", r.titulo AS "Título"
                FROM recientes r
                ORDER BY r.id DESC
                """)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un CTE tampoco puede saltarse la allowlist de columnas sensibles")
    void cteNoEvitaLaColumnaSensible() {
        assertThatThrownBy(() -> policy.validar("""
                WITH robes AS (
                    SELECT password_user AS secreto FROM users
                )
                SELECT secreto AS "X" FROM robes
                """)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password_user");
    }

    @Test
    @DisplayName("rechaza vacía")
    void rechazaVacia() {
        assertThatThrownBy(() -> policy.validar("  "))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM users",
            "UPDATE users SET status_user = 'ACTIVE'",
            "DROP TABLE ticket",
            "SELECT 1 FROM ticket; SELECT 2 FROM users"
    })
    @DisplayName("rechaza sentencias que no son de lectura")
    void rechazaEscritura(String sql) {
        assertThatThrownBy(() -> policy.validar(sql)).isInstanceOf(BadRequestException.class);
    }

@Test
    @DisplayName("la allowlist cubre las tablas que usan los reportes sembrados")
    void allowlistCubreLasSembradas() {
        assertThat(policy.tablasPermitidas())
                .contains("ticket", "users", "area", "category", "priority", "status",
                        "ticket_sla", "sla_policy", "ticket_rating");
    }

    @Test
    @DisplayName("se puede consultar la sede: es una dimension de los reportes")
    void sedeEsTablaPermitida() {
        assertThat(policy.tablasPermitidas()).contains("sucursal");
    }

    @Test
    @DisplayName("un reporte que une ticket con sede se acepta")
    void aceptaJoinConSucursal() {
        // Sin este caso, cualquier reporte que quiera desglosar por oficina
        // fallaria al generarse con "La tabla 'sucursal' no esta autorizada".
        assertThat(policy.tablasPermitidas()).contains("sucursal");

        policy.validar("SELECT t.id_ticket AS \"Ticket\", sc.name_sucursal AS \"Sede\" "
                + "FROM ticket t LEFT JOIN sucursal sc ON sc.id_sucursal = t.sucursal_id "
                + "WHERE (:sucursalId IS NULL OR t.sucursal_id = :sucursalId) "
                + "ORDER BY 1 DESC");
    }

    @Test
    @DisplayName(":sucursalId es un parametro admitido y recibe cast explicito")
    void sucursalIdEsParametroPermitido() {
        // El cast es lo que permite que el patron "(:sucursalId IS NULL OR ...)"
        // funcione con el parametro nulo: PostgreSQL no deduce el tipo solo.
        String conCast = policy.conCastosExplicitos(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t WHERE (:sucursalId IS NULL OR t.sucursal_id = :sucursalId)");
        assertThat(conCast).contains("CAST(:sucursalId AS UUID)");
    }

    @Test
    @DisplayName("otro parametro de sede inventado sigue rechazado")
    void parametrosDeSedeInventadosSeRechazan() {
        // La lista de parametros es cerrada: no se puede pedir un valor que el
        // backend nunca envia.
        assertThatThrownBy(() -> policy.validar(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t WHERE (:sede_id IS NULL OR t.sucursal_id = :sede_id)"))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------- SELECT ... INTO: DDL disfrazado de lectura ----------

    @Test
    @DisplayName("SELECT ... INTO crea una tabla y no puede colarse como lectura")
    void selectIntoSeRechaza() {
        // En PostgreSQL esto es una sola sentencia que CREA la tabla destino.
        // No lleva punto y coma y no contiene ninguna de las palabras que la
        // politica prohibia, asi que antes pasaba entera y era DDL efectivo
        // desde un endpoint descrito como de solo lectura, con la conexion de la
        // aplicacion.
        assertThatThrownBy(() -> policy.validar(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t INTO tabla_nueva"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("INTO");
    }

    @Test
    @DisplayName("SELECT INTO con lista explicita de columnas tambien se rechaza")
    void selectIntoColumnasSeRechaza() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t INTO nueva (a, b)"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("OVERWRITE tambien se rechaza: es la variante de PostgreSQL")
    void overwriteSeRechaza() {
        assertThatThrownBy(() -> policy.validar(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t INTO tabla OVERWRITE"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("INTO dentro de un texto literal no se confunde con la sentencia")
    void intoDentroDeLiteralSeAdmite() {
        // El nombre de una columna o un texto puede contener la palabra. Se
        // comprueba que el bloqueo es de palabra clave y no una busqueda a pelo,
        // porque si no un reporte legitimo con la columna "into_movimiento"
        // dejaria de poder guardarse.
        policy.validar("SELECT t.id_ticket AS \"Ticket\", t.into_movimiento AS \" intensively reviewed\" "
                + "FROM ticket t");
    }

    @Test
    @DisplayName("una columna que se llame INTO si se admite: es palabra clave, no substring")
    void columnaConNombreIntoSeAdmite() {
        policy.validar("SELECT t.id_ticket AS \"Ticket\", t.into_movimiento AS \"Ingreso\" FROM ticket t");
    }

    @Test
    @DisplayName("una palabra prohibida dentro de un identificador entrecomillado no se cuenta")
    void palabraProhibidaEnAliasEntrecomillado() {
        // Este caso fallaba antes: la deteccion de literales solo entendia
        // comillas simples, asi que el alias se tomaba por codigo y una columna
        // llamada "Revision UPDATE" hacia rechazar el reporte entero.
        policy.validar("SELECT t.id_ticket AS \"Ticket\", t.title_ticket AS \"Revision UPDATE\" "
                + "FROM ticket t");
    }

    @Test
    @DisplayName("una comilla escapada dentro de un texto no rompe la deteccion")
    void comillaEscapadaEnTexto() {
        // 'lo''hijo' es un unico literal con una comilla dentro. Contarlas en vez
        // de seguir el estado alternaba el modo yharia que lo que viene despues
        // parecer codigo.
        policy.validar("SELECT t.id_ticket AS \"Ticket\" FROM ticket t "
                + "WHERE t.title_ticket <> 'no ''UPDATE'' todavia'");
    }

    @Test
    @DisplayName("pero una sentencia prohibida de verdad sigue rechazada dentro de texto")
    void sentenciaRealSigueRechazada() {
        // El caso contrario del anterior: aflojar la deteccion no debe abrir la
        // puerta a un UPDATE de verdad.
        assertThatThrownBy(() -> policy.validar(
                "SELECT t.id_ticket AS \"Ticket\" FROM ticket t WHERE 'algo' = 'x' AND 1 = 1 "
                        + "UPDATE ticket SET title_ticket = 'x'"))
                .isInstanceOf(BadRequestException.class);
    }
}
