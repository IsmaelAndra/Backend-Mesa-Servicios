package com.mesaayuda.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los scripts SQL se ejecutan al arrancar y un error de sintaxis en ellos deja la
 * aplicación sin levantar, con un mensaje que no dice qué línea es.
 *
 * <p>Se comprueba lo mismo que comprueba Spring al cargarlos, porque validar solo
 * con psql no sirve: psql entiende el dollar-quoting de PostgreSQL
 * ({@code $$ ... $$}) y el troceador de sentencias de Spring no. Con los
 * plantillas de correo, que llevan HTML con estilos en línea, esa diferencia
 * partía cada plantilla por los {@code ;} de los atributos {@code style} y
 * reventaba con "error de sintaxis en o cerca de style".
 */
class SqlScriptsValidationTest {

    /**
     * Las migraciones de Flyway. Flyway sí entiende el dollar-quoting, pero se
     * mantiene la convención de comillas simples: así los scripts siguen siendo
     * legibles con el mismo troceador y las pruebas de abajo valen para todos.
     */
    private static final String[] SCRIPTS = {
            "db/migration/V1__esquema_base.sql",
            "db/migration/V2__bloqueo_tareas_programadas.sql",
            "db/migration/V3__ids_uuid.sql",
            "db/migration/V4__numero_ticket.sql",
            "db/migration/V5__reportes_numero_ticket.sql",
            "db/migration/V6__reporte_calificaciones_sin_uuid.sql",
            "db/migration/V7__textos_error_con_tildes.sql",
            "db/migration/V8__chat_edicion_y_respuestas_rapidas.sql",
            "db/migration/V9__paginas_aviso_configurables.sql",
            "db/migration/V10__cola_de_correo.sql",
            "db/migration/R__datos_base.sql",
            "db/dev/R__datos_dev.sql"};

    @Test
    void lasMigracionesTienenNombreValidoYVersionesUnicas() throws Exception {
        // Un nombre mal formado (un solo guion bajo, sin prefijo) Flyway lo
        // ignora en silencio: la migración no se aplica y nadie se entera.
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        var recursos = resolver.getResources("classpath*:db/**/*.sql");
        assertFalse(recursos.length == 0, "No se encontraron migraciones en db/");
        java.util.Set<String> versiones = new java.util.HashSet<>();
        for (var r : recursos) {
            String nombre = r.getFilename();
            assertTrue(nombre != null && nombre.matches("(V\\d+|R)__[a-z0-9_]+\\.sql"),
                    "Nombre de migración no válido: " + nombre);
            if (nombre.startsWith("V")) {
                String version = nombre.substring(1, nombre.indexOf("__"));
                assertTrue(versiones.add(version), "Versión repetida: V" + version);
            }
        }
    }

    @Test
    void losScriptsNoEmpiezanConByteOrderMark() throws Exception {
        // Con BOM, el primer carácter de la primera sentencia es U+FEFF y el
        // parser falla en la posición 1.
        for (String script : SCRIPTS) {
            byte[] bytes = leer(script);
            assertFalse(bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                            && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF,
                    script + " empieza con BOM");
        }
    }

    @Test
    void losScriptsNoUsanDollarQuoting() throws Exception {
        // ScriptUtils trocea por ';' respetando comillas simples, pero no
        // entiende $$, asi que el cuerpo de una plantilla se parte por los ';'
        // de sus estilos en linea.
        for (String script : SCRIPTS) {
            String texto = new String(leer(script), StandardCharsets.UTF_8);
            assertFalse(texto.contains("$$"),
                    script + " usa dollar-quoting ($$), que ScriptUtils no reconoce");
        }
    }

    @Test
    void todaSentenciaDelScriptQuedaConComillasCerradas() throws Exception {
        // Es el fallo concreto del dollar-quoting, medido con el mismo
        // troceador que usa la aplicacion: si al cortar queda una sentencia con
        // comillas sin cerrar, el cuerpo se partio por un ';'.
        for (String script : SCRIPTS) {
            List<String> sentencias = trocearComoSpring(script);
            assertFalse(sentencias.isEmpty(), script + " no produjo sentencias");

            int conProblema = 0;
            for (String sentencia : sentencias) {
                if (sentencia.isBlank() || comillasSinCerrar(sentencia) == 0) {
                    continue;
                }
                if (conProblema++ == 0) {
                    System.out.println("SENTENCIA ROTA >>> " + recortar(sentencia) + " <<<");
                }
            }
            assertEquals(0, conProblema,
                    script + " tiene sentencias con comillas sin cerrar, produits por un "
                            + "dollar-quoting o por un cuerpo sin escapar");
        }
    }

/**
 * Trocea el script igual que lo hace Spring al ejecutarlo.
 *
 * <p>ScriptUtils.splitSqlScript es de acceso de paquete, asi que se invoca por
 * reflexion con los mismos parametros que usa la aplicacion: separador ';',
 * comentarios '--' y bloque de comentario con asteriscos.
 */
    @SuppressWarnings("unchecked")
    private List<String> trocearComoSpring(String nombre) throws Exception {
        var recurso = new org.springframework.core.io.ClassPathResource(nombre);
        var codificado = new org.springframework.core.io.support.EncodedResource(
                recurso, java.nio.charset.StandardCharsets.UTF_8);

        java.lang.reflect.Method metodo = ScriptUtils.class.getDeclaredMethod(
                "splitSqlScript",
                org.springframework.core.io.support.EncodedResource.class,
                String.class, String.class, String[].class, String.class, String.class, List.class);
        metodo.setAccessible(true);

        List<String> destino = new java.util.ArrayList<>();
        metodo.invoke(null, codificado, ";", "--", new String[]{"--"}, "/*", "*/", destino);
        return destino;
    }

    @Test
    void ningunOnConflictSeQuedaSinSuInsertNiUpdate() throws Exception {
        // Al cortar y pegar una parte del script se perdió en su día la lista
        // VALUES del INSERT de plantillas y se quedó un ON CONFLICT suelto, que
        // falla al ejecutar. Se comprueba sobre las sentencias ya troceadas, no
        // contando caracteres, porque las plantillas ocupan lineas larguisimas.
        for (String script : SCRIPTS) {
            for (String sentencia : trocearComoSpring(script)) {
                String plana = sentencia.replaceAll("\\s+", " ").trim().toUpperCase();
                if (!plana.contains("ON CONFLICT")) {
                    continue;
                }
                assertTrue(plana.contains("INSERT") || plana.contains("UPDATE"),
                        script + " tiene un ON CONFLICT sin INSERT ni UPDATE en la misma "
                                    + "sentencia: " + recortar(sentencia));
            }
        }
    }

    /** Cuántas comillas simples quedan abiertas dentro de una sentencia. */
    private int comillasSinCerrar(String sentencia) {
        int abiertas = 0;
        for (int i = 0; i < sentencia.length(); i++) {
            char c = sentencia.charAt(i);
            if (c != '\'') {
                continue;
            }
            // Comilla simple escapada dentro del literal.
            if (i + 1 < sentencia.length() && sentencia.charAt(i + 1) == '\'') {
                i++;
                continue;
            }
            abiertas++;
        }
        // Siempre par: cada literal abre y cierra.
        return abiertas % 2;
    }

    private String recortar(String texto) {
        String plano = texto.replaceAll("\\s+", " ").trim();
        return plano.length() <= 140 ? plano : plano.substring(0, 140) + "...";
    }

    private byte[] leer(String nombre) throws Exception {
        try (InputStream in = new ClassPathResource(nombre).getInputStream()) {
            return in.readAllBytes();
        }
    }
}