package com.mesaayuda.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.mesaayuda.security.SecurityHeadersFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cabeceras de seguridad del perfil de producción.
 *
 * <p>Estas cabeceras se pueden perder sin que se rompa nada: la aplicación sigue
 * funcionando idéntica y un test que no mire las cabeceras no se entera. Por eso
 * hace falta una prueba que las compruebe explícitamente; si alguien las quita,
 * el fallo tiene que salir aquí y no en una revisión de seguridad dentro de seis
 * meses.
 */
class SecurityHeadersFilterTest {

    private static final String CSP = "default-src 'none'; frame-ancestors 'none'";

    private SecurityHeadersFilter crear(boolean habilitado, long hsts) {
        return new SecurityHeadersFilter(habilitado, CSP, hsts, "no-referrer");
    }

    private MockHttpServletResponse pasar(SecurityHeadersFilter f, MockHttpServletRequest req)
            throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, (r, s) -> { });
        return res;
    }

    @Test
    @DisplayName("en produccion pone CSP restrictiva")
    void poneCspRestrictiva() throws Exception {
        MockHttpServletResponse res = pasar(crear(true, 31536000),
                new MockHttpServletRequest("GET", "/api/admin/branding"));

        // Lo importante del valor: 'none'. El backend no sirve HTML, así que
        // cualquier cosa ejecutable que se colara aquí correría en el origen de la
        // API. Con 'none' el navegador no ejecuta nada.
        assertEquals(CSP, res.getHeader("Content-Security-Policy"));
        assertTrue(res.getHeader("Content-Security-Policy").contains("'none'"),
                "la CSP debe ser maxima, no una lista de excepciones");
    }

    @Test
    @DisplayName("en produccion pone nosniff, DENY y no-referrer")
    void poneLasCabecerasBasicas() throws Exception {
        MockHttpServletResponse res = pasar(crear(true, 31536000),
                new MockHttpServletRequest("GET", "/api/admin/branding"));

        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", res.getHeader("X-Frame-Options"));
        assertEquals("no-referrer", res.getHeader("Referrer-Policy"));
        // Los errores no deben quedar en la cache de un intermediate proxy.
        assertEquals("no-store", res.getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("HSTS solo se pone si la peticion llega por HTTPS")
    void hstsSoloConHttps() throws Exception {
        // Ponerlo sobre http sería mentira, y en local dejaría al navegador
        // recuerda que localhost es solo HTTPS, que no se puede deshacer.
        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/api/admin/branding");
        MockHttpServletResponse sinHttps = pasar(crear(true, 31536000), http);
        assertNull(sinHttps.getHeader("Strict-Transport-Security"),
                "una peticion http no debe prometer https");

        MockHttpServletRequest https = new MockHttpServletRequest("GET", "/api/admin/branding");
        https.setSecure(true);
        MockHttpServletResponse conHttps = pasar(crear(true, 31536000), https);
        assertNotNull(conHttps.getHeader("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("detecta HTTPS por X-Forwarded-Proto, que es lo unico que ve tras el proxy")
    void detectaHttpsPorElProxy() throws Exception {
        // Detras de un proxy el conector solo ve HTTP: es el proxy quien sabe
        // que por fuera era HTTPS. Si no se mirase esta cabecera, HSTS nunca se
        // pondria en el despliegue real, que es justo donde hace falta.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/branding");
        req.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse res = pasar(crear(true, 31536000), req);

        assertNotNull(res.getHeader("Strict-Transport-Security"));
        assertTrue(res.getHeader("Strict-Transport-Security").contains("max-age=31536000"));
    }

    @Test
    @DisplayName("con hsts a 0 no se pone la cabecera")
    void hstsDesactivadoConCero() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/branding");
        req.setSecure(true);
        MockHttpServletResponse res = pasar(crear(true, 0), req);

        assertNull(res.getHeader("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("en desarrollo no pone ninguna: HSTS rompe el trabajo local")
    void enDesarrolloNoAplica() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/branding");
        req.setSecure(true);
        MockHttpServletResponse res = pasar(crear(false, 31536000), req);

        assertNull(res.getHeader("Content-Security-Policy"),
                "activado en desarrollo, HSTS dejaria el navegador en modo https para siempre");
        assertNull(res.getHeader("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("la peticion sigue su camino pase lo que pase")
    void laPeticionSigueAdelante() throws Exception {
        boolean[] pasado = {false};
        MockHttpServletResponse res = new MockHttpServletResponse();

        crear(true, 31536000).doFilter(
                new MockHttpServletRequest("GET", "/api/admin/branding"), res,
                (r, s) -> pasado[0] = true);

        assertTrue(pasado[0], "el filtro no puede quedarse sin llamar a la cadena");
    }

    @Test
    @DisplayName("el perfil de produccion apaga Swagger y quita el DEBUG")
    void elPerfilDeProduccionEstaAjustado() throws Exception {
        // Estas comprobaciones leen el YAML tal cual se despliega. Un fallo aquí
        // significa que el fichero se ha editado mal, no que el código falle.
        String yaml = leer("application-prod.yml");

        assertTrue(yaml.contains("api-docs:"), "falta la seccion de api-docs");
        assertTrue(yaml.contains("enabled: false"),
                "swagger debe quedar apagado en produccion");
        assertTrue(yaml.contains("include-message: never"),
                "los errores no deben incluir el mensaje interno");
        assertTrue(yaml.contains("include-stacktrace: never"),
                "los errores no deben incluir la traza");
        assertTrue(yaml.contains("include-exception: false"));
        assertTrue(yaml.contains("com.mesaayuda: INFO"),
                "DEBUG en produccion vuelca datos de negocio al log");
        assertTrue(!yaml.contains("com.mesaayuda: DEBUG"),
                "no debe quedar DEBUG para la aplicacion");
    }

    @Test
    @DisplayName("el perfil general NO apaga Swagger, para no romper el desarrollo")
    void elPerfilGeneralDejaSwagger() throws Exception {
        String yaml = leer("application.yml");
        // Si se apagara aqui, nadie podria documentar ni probar la API en local.
        // El apagado va solo en el perfil de produccion.
        assertTrue(!yaml.contains("api-docs:\n    enabled: false"),
                "Swagger debe apagarse en prod, no en el perfil general");
    }

    private String leer(String nombre) throws Exception {
        java.io.InputStream in = getClass().getClassLoader()
                .getResourceAsStream(nombre);
        assertNotNull(in, "no se encuentra " + nombre);
        try (in) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}