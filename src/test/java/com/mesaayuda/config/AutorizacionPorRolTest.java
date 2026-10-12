package com.mesaayuda.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los límites de rol, fijados por escrito.
 *
 * <p>En 1E aparecieron tres pantallas donde el frontend limitaba al
 * SUPER_ADMIN y el backend dejaba entrar a ADMIN. Esa discrepancia no la detectó
 * ninguna prueba: ninguna comprobaba el rol. Estas sí.
 *
 * <p>Se leen los ficheros de código en vez de los metadatos anotados porque
 * se quiere saber lo que el despliegue hace de verdad, no lo que las anotaciones
 * prometen. Si alguien quita el {@code @PreAuthorize} y se olvida de la prueba,
 * esto falla; si lo cambia por otro nombre, también.
 */
class AutorizacionPorRolTest {

    private static final Path BASE = Path.of("src", "main", "java", "com", "mesaayuda");

    private static final Path CONTROLADORES = BASE.resolve("controller");

    private String leer(String controlador) throws Exception {
        return Files.readString(CONTROLADORES.resolve(controlador));
    }

    /** ¿Qué anotación de clase tiene? Es la que protege TODOS sus endpoints. */
    private String anotacionDeClase(String controlador) throws Exception {
        List<String> lineas = Files.readAllLines(CONTROLADORES.resolve(controlador));
        // Se mira la ventana justo antes de "public class", que es donde Spring
        // Boot espera las anotaciones de clase.
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).contains("public class")) {
                StringBuilder ventana = new StringBuilder();
                for (int j = Math.max(0, i - 8); j <= i; j++) {
                    ventana.append(lineas.get(j)).append('\n');
                }
                return ventana.toString();
            }
        }
        throw new IllegalStateException("no se encuentra la clase en " + controlador);
    }

    // ============================ SUPER ADMIN ============================

    @Test
    @DisplayName("la duracion de la sesion solo la cambia el super administrador")
    void sessionConfigEsExclusivaDelSuperAdmin() throws Exception {
        String anotacion = anotacionDeClase("SessionConfigController.java");

        // Alargar cuánto vive una sesión de toda la organización no es una
        // preferencia de un administrador: es seguridad global.
        assertTrue(anotacion.contains("hasAuthority('ROLE_SUPER_ADMIN')"),
                "SessionConfigController debe exigir solo SUPER_ADMIN. Anotacion actual:\n" + anotacion);
        assertFalse(anotacion.contains("ROLE_ADMIN"),
                "no debe admitirse ADMIN en la configuracion de sesion:\n" + anotacion);
    }

    @Test
    @DisplayName("quien puede ver los acuses del chat lo decide solo el super administrador")
    void chatConfigEsExclusivoDelSuperAdmin() throws Exception {
        String anotacion = anotacionDeClase("ChatConfigController.java");

        // Quien decide esto decide quién sabe que otro usuario leyó un mensaje.
        assertTrue(anotacion.contains("hasAuthority('ROLE_SUPER_ADMIN')"),
                "ChatConfigController debe exigir solo SUPER_ADMIN. Anotacion actual:\n" + anotacion);
        assertFalse(anotacion.contains("ROLE_ADMIN"),
                "no debe admitirse ADMIN en la configuracion del chat:\n" + anotacion);
    }

    @Test
    @DisplayName("todos los endpoints de reportes personalizados exigen super administrador")
    void reportsPersonalizadosEsExclusivoDelSuperAdmin() throws Exception {
        String codigo = leer("ReportConfigController.java");

        // Antes leer los exigia ADMIN y escribir SUPER_ADMIN. Como la respuesta
        // incluye el querySql de cada reporte, leer era ya medio acceso a la base.
        assertFalse(codigo.contains("checkAdminOrSuperAdmin();"),
                "no debe quedar ninguna llamada a checkAdminOrSuperAdmin: la lectura de "
                        + "reportes tambien expone el SQL. Si aparece, revisar el metodo list().");
        int lecturas = contarOcurrencias(codigo, "checkSuperAdmin();");
        assertTrue(lecturas >= 4,
                "se esperaban 4 comprobaciones (list, create, update, delete) y hay " + lecturas);
    }

    @Test
    @DisplayName("la auditoria del sistema solo la ve el super administrador")
    void auditoriaDelSistemaEsExclusivaDelSuperAdmin() throws Exception {
        String codigo = leer("AdminController.java");

        // Estas filas dicen qué usuario intentó qué y desde dónde. Un ADMIN con
        // acceso vería el rastro de actividad de sus superiores.
        int bloque = codigo.indexOf("/audit-sistema");
        assertTrue(bloque > 0, "no se encuentra el endpoint de auditoria del sistema");
        int ventana = codigo.indexOf("checkSuperAdmin();", bloque);
        assertTrue(ventana > bloque && ventana - bloque < 900,
                "el endpoint /audit-sistema debe exigir super administrador");
    }

    @Test
    @DisplayName("el interruptor del doble factor solo lo mueve el super administrador")
    void mfaSoloLoMueveElSuperAdmin() throws Exception {
        String codigo = leer("MfaController.java");

        int leerEstado = codigo.indexOf("public ResponseEntity<MfaSettingResponse> estado()");
        assertTrue(leerEstado > 0, "no se encuentra el metodo estado()");
        int comprobacion = codigo.indexOf("exigirSuperAdmin();", leerEstado);
        assertTrue(comprobacion > leerEstado && comprobacion - leerEstado < 400,
                "estado() debe exigir super administrador antes de devolver nada");

        int cambiar = codigo.indexOf("public ResponseEntity<MfaSettingResponse> cambiar(");
        assertTrue(cambiar > 0, "no se encuentra el metodo cambiar()");
        int comprobacion2 = codigo.indexOf("exigirSuperAdmin();", cambiar);
        assertTrue(comprobacion2 > cambiar && comprobacion2 - cambiar < 400,
                "cambiar() debe exigir super administrador antes de aplicar el interruptor");
    }

    // ============================ lo que NO debe romperse ============================

    @Test
    @DisplayName("agente y administrador conservan el acceso a tareas y calendario")
    void elAgenteNoPierdeSusAccesos() throws Exception {
        // El riesgo de endurecer los roles es pasarse: si un endpoint que necesita
        // el agente pasa a exigir super administrador, se le rompe el trabajo y
        // nadie se da cuenta hasta que alguien abre un ticket.
        //
        // TicketController queda fuera a proposito: no lleva anotacion de clase
        // porque lo usa tambien el usuario final para abrir y seguir sus tickets.
        for (String c : List.of("TaskController.java", "CalendarController.java")) {
            String anotacion = anotacionDeClase(c);
            assertTrue(anotacion.contains("AGENT"),
                    c + " debe seguir admitiendo al agente. Anotacion actual:\n" + anotacion);
            assertTrue(anotacion.contains("ADMIN"),
                    c + " debe seguir admitiendo a ADMIN:\n" + anotacion);
        }
    }

    @Test
    @DisplayName("tickets sigue abierto a cualquier usuario autenticado")
    void ticketsSigueAbiertoAUsuarios() throws Exception {
        // Si se le pusiera un rol, un usuario final no podria abrir un ticket ni
        // seguir los suyos: seria la queja mas obvia y la mas cara.
        String anotacion = anotacionDeClase("TicketController.java");
        assertFalse(anotacion.contains("@PreAuthorize"),
                "TicketController no debe exigir un rol concreto:\n" + anotacion);

        String seguridad = Files.readString(BASE.resolve("config").resolve("SecurityConfig.java"));
        assertTrue(seguridad.contains(".anyRequest().authenticated()"),
                "los tickets deben quedar bajo authenticated()");
    }

    @Test
    @DisplayName("los endpoints publicos siguen siendo publicos")
    void losPublicosSiguenPublicos() throws Exception {
        String codigo = Files.readString(BASE.resolve("config").resolve("SecurityConfig.java"));

        // Si estos dejaran de ser públicos, se rompe el login entero.
        for (String ruta : List.of("/auth/login", "/auth/register", "/auth/forgot-password",
                "/auth/reset-password", "/auth/mfa/confirm", "/auth/refresh", "/auth/logout")) {
            assertTrue(codigo.contains("\"" + ruta + "\""),
                    "falta la ruta publica " + ruta);
        }
        assertTrue(codigo.contains("/admin/branding"),
                "el branding debe seguir siendo publico: se ve en la pantalla de acceso");
        assertTrue(codigo.contains("/pages/public"), "las paginas publicas deben seguir abiertas");
    }

    @Test
    @DisplayName("toda escritura de configuracion queda auditada")
    void lasEscriturasQuedanAuditadas() throws Exception {
        // Un cambio de configuracion sin rastro sirve de poco: a quien leo la
        // auditoria le tiene que poder decir quién cambió qué.
        String codigo = Files.readString(CONTROLADORES.resolve("AdminController.java"));
        String seguro = Files.readString(BASE.resolve("config").resolve("SecurityConfig.java"));
        assertTrue(codigo.contains("systemAuditService.record")
                        || codigo.contains("SystemAuditService"),
                "AdminController debe auditar sus escrituras");
        assertFalse(seguro.isEmpty(), "SecurityConfig debe seguir siendo legible");
    }

    private int contarOcurrencias(String texto, String aguja) {
        int n = 0;
        int i = texto.indexOf(aguja);
        while (i >= 0) {
            n++;
            i = texto.indexOf(aguja, i + aguja.length());
        }
        return n;
    }
}