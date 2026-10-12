package com.mesaayuda.integracion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas contra un PostgreSQL de verdad (Testcontainers).
 *
 * <p>Las pruebas unitarias usan mocks y no ven nada de lo que solo decide la base:
 * una consulta JPQL que PostgreSQL no sabe tipar, un CHECK que rechaza un estado,
 * una columna que el entity llama distinto. Ya pasó con {@code status_node_run}
 * y con los parámetros de fecha nulos (ver AGENTS.md).
 *
 * <p>Arrancar el contexto JPA ya es la prueba principal:
 * <ul>
 *   <li>Flyway aplica todas las migraciones sobre una base vacía.</li>
 *   <li>{@code ddl-auto=validate}: Hibernate comprueba que cada entity casa con
 *       sus tablas y columnas.</li>
 *   <li>Spring Data valida el JPQL de cada {@code @Query} al crear los repositorios.</li>
 * </ul>
 *
 * <p>Sin Docker se saltan solas ({@code disabledWithoutDocker}); en CI sí corren.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration"
})
class BaseDeDatosPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void lasMigracionesSeAplicanEnUnaBaseVacia() {
        var actual = flyway.info().current();
        assertNotNull(actual, "Flyway no aplicó ninguna migración");
        assertTrue(Integer.parseInt(actual.getVersion().getVersion()) >= 2,
                "Falta aplicar alguna migración versionada: " + actual.getVersion());
        assertEquals(0, flyway.info().pending().length, "Quedan migraciones pendientes");
    }

    @Test
    void losDatosBaseSiembranLosRoles() {
        Integer roles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role WHERE name_role IN ('SUPER_ADMIN','ADMIN','AGENT','USER')",
                Integer.class);
        assertEquals(4, roles);
    }

    @Test
    void elEsquemaBaseSePuedeReaplicar() throws Exception {
        // Es lo que pasa en una base que existía antes de Flyway: se marca en la
        // versión 0 y V1 se aplica encima de lo que ya hay. Si V1 dejara de ser
        // idempotente, esas bases no arrancarían.
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V1__esquema_base.sql"));
        }
    }

    @Test
    void laAuditoriaEsSoloDeInsercion() {
        jdbc.update("INSERT INTO audit_log (action) VALUES ('PRUEBA')");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE audit_log SET action = 'ALTERADA' WHERE action = 'PRUEBA'"));
    }

    @Test
    void elEstadoDeUnPasoSoloAdmiteLosValoresDelCheck() {
        String definicion = jdbc.queryForObject("""
                SELECT string_agg(pg_get_constraintdef(c.oid), ' ')
                FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                WHERE t.relname = 'workflow_node_run' AND c.contype = 'c'
                """, String.class);
        assertNotNull(definicion, "workflow_node_run no tiene CHECK");
        for (String estado : new String[]{"EJECUTANDO", "COMPLETADO", "ERROR", "OMITIDO"}) {
            assertTrue(definicion.contains(estado), "El CHECK no admite " + estado);
        }
    }

    @Test
    void laBusquedaGlobalTieneSuExtensionEIndices() {
        Integer extension = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        assertEquals(1, extension);
        Integer indices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexdef ILIKE '%gin_trgm_ops%'", Integer.class);
        assertTrue(indices != null && indices >= 8, "Faltan índices trigram: " + indices);
    }

    @Test
    void existeLaTablaDeBloqueoDeTareas() {
        Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM shedlock", Integer.class);
        assertEquals(0, filas);
    }
}
