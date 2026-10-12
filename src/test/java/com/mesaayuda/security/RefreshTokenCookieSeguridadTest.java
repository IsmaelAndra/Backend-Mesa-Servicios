package com.mesaayuda.security;

import static com.mesaayuda.IdsPrueba.id;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El refresh token sale de localStorage y entra en una cookie HttpOnly.
 *
 * <p>Lo que protege esto: un token en localStorage lo lee cualquier script que se
 * ejecute en la pagina. Con solo eso, una inyeccion de script (un comentario sin
 * escapar, un componente de terceros) se lleva una sesion de 30 dias aunque el
 * token de acceso caduque a los quince minutos.
 *
 * <p>Las pruebas comprueban las tres condiciones sin las que el cambio no sirve de
 * nada: HttpOnly puesto, y CSRF con doble envio. Con la cookie puesta y el CSRF
 * ausente, la sesion sigue siendo robable desde otro sitio, solo que mas
 * laborioso.
 */
class RefreshTokenCookieSeguridadTest {

    private static final long TREINTA_DIAS = 30L * 24 * 60 * 60 * 1000;

    private MockHttpServletResponse emitir(boolean seguro) {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new RefreshTokenCookie(seguro, TREINTA_DIAS).emitir(respuesta, "token-secreto");
        return respuesta;
    }

    private List<String> cabeceras(MockHttpServletResponse r) {
        return r.getHeaders("Set-Cookie");
    }

    private String cabeceraDe(MockHttpServletResponse r, String nombre) {
        return cabeceras(r).stream()
                .filter(h -> h.startsWith(nombre + "="))
                .findFirst()
                .orElse(null);
    }

    // ============================ HttpOnly ============================

    @Test
    @DisplayName("la cookie del refresh token es HttpOnly")
    void elRefreshTokenEsHttpOnly() {
        String refresh = cabeceraDe(emitir(false), RefreshTokenCookie.COOKIE_REFRESH);

        assertTrue(refresh != null && refresh.contains("HttpOnly"),
                "sin HttpOnly el JavaScript de la pagina puede leerlo: " + refresh);
        assertTrue(refresh.contains("token-secreto"));
    }

    @Test
    @DisplayName("el token NO viaja en el cuerpo de la respuesta")
    void elTokenNoVaEnElJson() {
        // Es la mitad del asunto: la cookie evita que el navegador lo guarde en
        // localStorage, pero si ademas viaja en el JSON, el frontend lo volveria a
        // guardar y no habriamos cambiado nada.
        com.mesaayuda.dto.AuthResponse conToken = construirRespuesta("el-token");
        com.mesaayuda.dto.AuthResponse sinToken = conToken.sinRefreshToken();

        assertEquals("el-token", conToken.refreshToken());
        assertNull(sinToken.refreshToken(), "el refresh token no debe viajar en el JSON");
        assertEquals("el-acceso", sinToken.token(), "el token de acceso sigue siendo publico");
    }

    @Test
    @DisplayName("la cookie CSRF es legible a proposito, pero sin el token de refresco")
    void laCsrfEsLegibleYNoTraeElToken() {
        String csrf = cabeceraDe(emitir(false), RefreshTokenCookie.COOKIE_CSRF);

        // Sin HttpOnly, porque su unico trabajo es que el frontend lo copie a la
        // cabecera. Y sin el token de refresco, que va en su propia cookie.
        assertFalse(csrf.contains("HttpOnly"),
                "la cookie CSRF debe ser legible para poder copiarla: " + csrf);
        assertFalse(csrf.contains("token-secreto"),
                "el token de refresco no puede ir en la cookie CSRF: " + csrf);
    }

    @Test
    @DisplayName("la cookie CSRF no lleva nada utilizable por si sola")
    void laCsrfNoSirveSola() {
        MockHttpServletResponse r = emitir(false);
        String csrf = cabeceraDe(r, RefreshTokenCookie.COOKIE_CSRF);

        String valor = csrf.substring(csrf.indexOf('=') + 1, csrf.indexOf(';'));
        assertTrue(valor.length() >= 30, "debe ser un valor impredecible: " + valor);
        assertFalse(valor.contains("token-secreto"));
    }

    // ============================ Atributos ============================

    @Test
    @DisplayName("la cookie va con Secure solo si se pide")
    void secureSoloEnProduccion() {
        assertFalse(cabeceraDe(emitir(false), RefreshTokenCookie.COOKIE_REFRESH).contains("Secure"),
                "en http://localhost el navegador rechaza Secure y no habria sesion");

        // En produccion con HTTPS es obligatorio: sin Secure, el token viaja en
        // claro y basta con mirar la red.
        assertTrue(cabeceraDe(emitir(true), RefreshTokenCookie.COOKIE_REFRESH).contains("Secure"));
    }

    @Test
    @DisplayName("SameSite=Lax, no None")
    void sameSiteLax() {
        String refresh = cabeceraDe(emitir(true), RefreshTokenCookie.COOKIE_REFRESH);

        assertTrue(refresh.contains("SameSite=Lax"), refresh);
        // None + sin CSRF seria un agujero; Lax es el que cierra el POST cruzado.
        assertFalse(refresh.contains("SameSite=None"), refresh);
    }

    @Test
    @DisplayName("el camino se limita a /api/auth")
    void elCaminoEstaAcotado() {
        String refresh = cabeceraDe(emitir(false), RefreshTokenCookie.COOKIE_REFRESH);

        assertTrue(refresh.contains("Path=/api/auth"),
                "con Path=/ el navegador la envia a cada peticion, incluidas las de lectura: " + refresh);
    }

    @Test
    @DisplayName("la CSRF va en Path=/ para que la SPA pueda leerla y renovar la sesión")
    void laCsrfLaPuedeLeerLaPagina() {
        String csrf = cabeceraDe(emitir(false), RefreshTokenCookie.COOKIE_CSRF);

        // Con Path=/api/auth, document.cookie no la mostraba en ninguna página de
        // la aplicación: la SPA no podía enviar la cabecera X-XSRF-TOKEN y cerraba
        // la sesión cada vez que caducaba el token de acceso.
        assertTrue(csrf.contains("Path=/;"), csrf);
        assertTrue(!csrf.contains("HttpOnly"), csrf);
    }

    @Test
    @DisplayName("Max-Age coincide con la vigencia del refresh")
    void elMaxAgeCoincideConLaVigencia() {
        String refresh = cabeceraDe(emitir(false), RefreshTokenCookie.COOKIE_REFRESH);

        assertTrue(refresh.contains("Max-Age=" + (TREINTA_DIAS / 1000)),
                "una vigencia distinta haria que la cookie muera antes que el token: " + refresh);
    }

    // ============================ CSRF: doble envio ============================

    @Test
    @DisplayName("con cabecera y cookie CSRF iguales, la peticion pasa")
    void dobleEnvioValido() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = peticionCon("el-token-csrf");
        p.addHeader(RefreshTokenCookie.CABECERA_CSRF, "el-token-csrf");

        assertTrue(c.csrfValido(p));
    }

    @Test
    @DisplayName("una pagina de otro sitio SIN la cabecera CSRF no puede renovar")
    void sinCabeceraNoPasa() {
        // Este es el ataque: el atacante induce al navegador a hacer POST a
        // /auth/refresh. La cookie HttpOnly se envia sola porque el navegador
        // adjunta las credenciales, pero el atacante no puede leer la cookie CSRF
        // de origen cruzado, asi que no puede rellenar la cabecera.
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = peticionCon("el-token-csrf");

        assertFalse(c.csrfValido(p),
                "sin CSRF, cualquier pagina podria renovar la sesion del usuario");
    }

    @Test
    @DisplayName("una cabecera con valor inventado no pasa")
    void cabeceraInventadaNoPasa() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = peticionCon("el-token-csrf");
        p.addHeader(RefreshTokenCookie.CABECERA_CSRF, "otro-token");

        assertFalse(c.csrfValido(p));
    }

    @Test
    @DisplayName("una cabecera en mayusculas (otro caso) no cuela")
    void elValorNoSeNormaliza() {
        // A propósito: compararlo sin normalizar evita que dos representaciones
        // distintas del mismo valor se tengan por iguales donde no lo son.
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = peticionCon("El-Token-Csrf");
        p.addHeader(RefreshTokenCookie.CABECERA_CSRF, "el-token-csrf");

        assertFalse(c.csrfValido(p));
    }

    @Test
    @DisplayName("sin cookies en la peticion, no hay CSRF que valido")
    void sinCookiesNoPasa() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = new MockHttpServletRequest("POST", "/api/auth/refresh");
        p.addHeader(RefreshTokenCookie.CABECERA_CSRF, "lo-que-sea");

        assertFalse(c.csrfValido(p));
    }

    // ============================ Lectura y borrado ============================

    @Test
    @DisplayName("el refresh se lee de la cookie, no del cuerpo")
    void elRefreshSeLeeDeLaCookie() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = new MockHttpServletRequest("POST", "/api/auth/refresh");
        p.setCookies(new Cookie(RefreshTokenCookie.COOKIE_REFRESH, "el-refresh"));

        assertEquals("el-refresh", c.leerRefresh(p));
    }

    @Test
    @DisplayName("sin cookie de refresco devuelve null, no una cadena vacia")
    void sinCookieDevuelveNull() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletRequest p = new MockHttpServletRequest("POST", "/api/auth/refresh");
        p.setCookies(new Cookie("otra", "valor"));

        // Null y "" se distinguen al probar: con "" el logout podria intentar
        // revocar un token vacio y enmascarar el problema real.
        assertNull(c.leerRefresh(p));
    }

    @Test
    @DisplayName("borrar usa el mismo camino que emitir, si no la cookie sobrevive")
    void borrarUsaElMismoCamino() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletResponse r = new MockHttpServletResponse();
        c.borrar(r);

        String refresh = cabeceraDe(r, RefreshTokenCookie.COOKIE_REFRESH);
        List<String> csrf = cabeceras(r).stream()
                .filter(h -> h.startsWith(RefreshTokenCookie.COOKIE_CSRF + "=")).toList();

        // Si el Path fuera distinto al de emision, el navegador guardaria una
        // segunda cookie con el mismo nombre y la de sesion seguiria viva.
        assertTrue(refresh.contains("Path=/api/auth"), refresh);
        assertTrue(refresh.contains("Max-Age=0"), refresh);
        // La CSRF se borra en su camino actual (/) y en el antiguo (/api/auth),
        // para no dejar copias viejas en navegadores que ya la tenían.
        assertTrue(csrf.stream().anyMatch(h -> h.contains("Path=/;") && h.contains("Max-Age=0")), csrf::toString);
        assertTrue(csrf.stream().anyMatch(h -> h.contains("Path=/api/auth") && h.contains("Max-Age=0")), csrf::toString);
    }

    @Test
    @DisplayName("borrar conserva HttpOnly en la cookie del token")
    void borrarMantieneHttpOnly() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletResponse r = new MockHttpServletResponse();
        c.borrar(r);

        // Sin esto, la cookie de borrado no podria borrar la original: el
        // navegador las trata como distintas por los atributos.
        assertTrue(cabeceraDe(r, RefreshTokenCookie.COOKIE_REFRESH).contains("HttpOnly"));
    }

    @Test
    @DisplayName("se emiten exactamente dos cookies, no mas")
    void soloDosCookies() {
        // Una cookie de mas con el token dentro seria un agujero: da igual que
        // tenga HttpOnly, si existe alguna via por la que se lea, no sirve.
        List<String> cookies = cabeceras(emitir(true));

        assertEquals(2, cookies.size(), "cookies emitidas: " + cookies);
        assertEquals(1, cookies.stream()
                .filter(h -> h.contains("token-secreto")).count(),
                "el token solo puede ir en su propia cookie");
    }

    @Test
    @DisplayName("dos emisiones seguidas no reutilizan el token CSRF")
    void cadaEmisionTieneSuCsrf() {
        RefreshTokenCookie c = new RefreshTokenCookie(false, TREINTA_DIAS);
        MockHttpServletResponse a = new MockHttpServletResponse();
        MockHttpServletResponse b = new MockHttpServletResponse();
        c.emitir(a, "token-a");
        c.emitir(b, "token-b");

        assertFalse(csrfDe(a).equals(csrfDe(b)),
                "si el CSRF se reutilizara, un atacante que lea uno valdría para todos");
    }

    private String csrfDe(MockHttpServletResponse r) {
        String h = cabeceraDe(r, RefreshTokenCookie.COOKIE_CSRF);
        return h.substring(h.indexOf('=') + 1, h.indexOf(';'));
    }

    private MockHttpServletRequest peticionCon(String valorCsrf) {
        MockHttpServletRequest p = new MockHttpServletRequest("POST", "/api/auth/refresh");
        p.setCookies(new Cookie[]{
                new Cookie(RefreshTokenCookie.COOKIE_REFRESH, "el-refresh"),
                new Cookie(RefreshTokenCookie.COOKIE_CSRF, valorCsrf)
        });
        return p;
    }

    private com.mesaayuda.dto.AuthResponse construirRespuesta(String refresh) {
        return new com.mesaayuda.dto.AuthResponse(
                "el-acceso", "Bearer", id(1), "a@coacmagisterio.fin.ec", "USER", "TI",
                Arrays.asList("LEER"), false, refresh, 900L, 5, true, true, 15, null);
    }
}