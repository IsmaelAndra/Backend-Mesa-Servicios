package com.mesaayuda.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saca el refresh token de localStorage y lo pone en una cookie HttpOnly.
 *
 * <p>La motivation es una sola: un token en localStorage lo lee cualquier
 * script que se ejecute en la pagina. Con una sola inyeccion de script —un
 * comentario sin escapar, un componente de terceros, una extension— el atacante
 * se lleva el refresh token y con el mantiene la sesion abierta aunque el token
 * de acceso caduque a los quince minutos. Ademas se queda 30 dias guardado en el
 * disco del navegador.
 *
 * <p>Con HttpOnly, ese script no puede leerlo. Sigue pudiendo llamar a /auth/refresh
 * desde la pagina, pero con la cookie solo, y cada rotacion deja el token viejo
 * invalido (con deteccion de reutilizacion incluida).
 *
 * <p>La consecuencia es que una peticion con credenciales puede venir de otra
 * pagina: de ahi el doble envio. Ademas de la cookie HttpOnly se escribe una
 * cookie legible, el token CSRF, que el frontend tiene que devolver en la
 * cabecera {@code X-XSRF-TOKEN}. Un atacante desde otro sitio puede hacer que el
 * navegador envie la cookie, pero no puede leer la segunda para copiarla en la
 * cabecera.
 *
 * <p>Se usa patron {@code SameSite=Lax} en lugar del {@code None} que
 * permitiria el patron doble sin CSRF. Lax ya bloquea el caso cross-site POST, que
 * es el que importa aqui, y es mas estricto: no depende de que nadie se equivoque
 * al copiar la cookie.
 */
@Component
public class RefreshTokenCookie {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCookie.class);

    /** Cookie con el token de refresco. HttpOnly: el JavaScript no la ve. */
    public static final String COOKIE_REFRESH = "mesa_refresh";

    /**
     * Cookie con el token CSRF. Esta SÍ es legible a proposito: es lo que permite
     * al frontend copiarla en la cabecera. No da acceso a nada por si sola.
     */
    public static final String COOKIE_CSRF = "mesa_xsrf";

    /** Cabecera donde el frontend devuelve el token CSRF. */
    public static final String CABECERA_CSRF = "X-XSRF-TOKEN";

    /**
     * El camino se limita a /api/auth.
     *
     * <p>Con la cookie en "/" el navegador la envia a cada peticion, incluidas
     * las de lectura. Con "/api/auth" solo viaja a los tres endpoints que la
     * necesitan, que es exactamente donde puede hacer falta.
     */
    private static final String CAMINO = "/api/auth";

    private final boolean seguro;
    private final Duration vigencia;

    public RefreshTokenCookie(
            @Value("${app.auth.secure-cookie:false}") boolean seguro,
            @Value("${app.jwt.refresh-expiration-ms:2592000000}") long refreshExpirationMs) {
        this.seguro = seguro;
        if (!seguro) {
            log.warn("La cookie de sesión se emite SIN Secure. Es lo necesario en "
                    + "http://localhost, pero en producción con HTTPS hay que poner "
                    + "APP_SECURE_COOKIE=true: si no, el token de refresco puede viajar "
                    + "en claro y alguien que lo intercepte mantiene la sesión 30 días.");
        }
        this.vigencia = Duration.ofMillis(refreshExpirationMs);
    }

    /**
     * Emite el par de cookies tras un login o un refresco.
     *
     * @param refreshToken valor en claro, el mismo que antes viajaba en el JSON
     */
    public void emitir(HttpServletResponse response, String refreshToken) {
        // El token CSRF se genera junto al par, no se pide aparte: si fuera
        // independiente, un atacante que consiga el token CSRF por su cuenta
        // podría combinarlo con la cookie robada.
        String csrf = UUID.randomUUID().toString();

        response.addHeader("Set-Cookie", construir(COOKIE_REFRESH, refreshToken, true, vigencia));
        response.addHeader("Set-Cookie", construir(COOKIE_CSRF, csrf, false, vigencia));
    }

    /** Borra las dos cookies. Se llama en logout y en error de refresh. */
    public void borrar(HttpServletResponse response) {
        // Mismo camino y atributos que al emitirlas: si no, el navegador guarda
        // una segunda cookie con el mismo nombre pero distinta ruta y no la borra.
        response.addHeader("Set-Cookie", construir(COOKIE_REFRESH, "", true, Duration.ZERO));
        response.addHeader("Set-Cookie", construir(COOKIE_CSRF, "", false, Duration.ZERO));
        // La CSRF se emitía antes en /api/auth; se borra también ahí para que
        // no quede una copia vieja en los navegadores que ya la tenían.
        response.addHeader("Set-Cookie", COOKIE_CSRF + "=; Path=" + CAMINO + "; Max-Age=0; SameSite=Lax"
                + (seguro ? "; Secure" : ""));
    }

    /**
     * Comprueba el doble envio.
     *
     * <p>Solo compara, no valida contra nada guardado en el servidor: no hace
     * falta, porque el atacante no puede leer la cookie CSRF de origen cruzado.
     * Lo que se evita es que una pagina suya haga que el navegador use la sesion
     * del usuario sin que este lo note.
     */
    public boolean csrfValido(HttpServletRequest request) {
        String cabecera = request.getHeader(CABECERA_CSRF);
        if (cabecera == null || cabecera.isBlank()) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (COOKIE_CSRF.equals(c.getName())) {
                // Comparacion en tiempo constante para no filtrar el valor entero de un
                // vistazo. Aquí no es imprescindible, porque un atacante no puede
                // medir nada útil a través de la red, pero no cuesta nada.
                return MessageDigest.isEqual(
                        c.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        cabecera.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return false;
    }

    /** Refresh token de la peticion, o null si no viene. */
    public String leerRefresh(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_REFRESH.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private String construir(String nombre, String valor, boolean httpOnly, Duration duracion) {
        StringBuilder c = new StringBuilder();
        c.append(nombre).append('=').append(valor);
        // La de refresh (HttpOnly) solo viaja a /api/auth. La CSRF va en "/":
        // tiene que poder LEERLA el JavaScript de la aplicación para copiarla en
        // la cabecera, y document.cookie solo muestra las cookies cuyo Path
        // cubre la página. Con /api/auth la SPA no la veía nunca: no enviaba la
        // cabecera, ni siquiera intentaba renovar, y cerraba la sesión al
        // caducar el token. No es secreta para el propio origen: el doble envío
        // se apoya en que otro origen no puede leerla.
        c.append("; Path=").append(httpOnly ? CAMINO : "/");
        c.append("; Max-Age=").append(Math.max(0, duracion.toSeconds()));
        if (httpOnly) {
            c.append("; HttpOnly");
        }
        // Secure es obligatorio en HTTPS. En http://localhost se deja fuera a
        // proposito: el navegador rechazaria la cookie y no se podria iniciar
        // sesion en desarrollo.
        if (seguro) {
            c.append("; Secure");
        }
        // Lax, no None: bloquea el POST de origen cruzado, que es el unico caso
        // que importa para /auth/refresh y /auth/logout.
        c.append("; SameSite=Lax");
        return c.toString();
    }
}