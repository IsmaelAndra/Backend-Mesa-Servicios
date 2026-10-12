package com.mesaayuda.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeceras de seguridad en las respuestas del backend.
 *
 * <p>No es una ornamental. La más importante aquí es
 * {@code Content-Security-Policy: default-src 'none'}, y la razón es concreta:
 * <b>este backend no sirve HTML</b>, lo sirve la SPA de Angular. Si alguien
 * encontrara una forma de que el backend devolviera una página (un HTML de error
 * sin escapar, un recurso mal configurado), esa página se ejecutaría en el mismo
 * origen que la API, con acceso a lo que la página sirva. Con {@code 'none'} el
 * navegador no ejecuta nada de lo que llegue de aquí.
 *
 * <p>Se habilita por propiedad para no activarla sin querer en desarrollo, donde
 * estorba: HSTS hace que el navegador guarde que ese origen es solo HTTPS, y eso
 * no se puede deshacer desde el código. En local rompería todo el desarrollo
 * siguiente.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final boolean habilitado;
    private final String csp;
    private final long hstsMaxAge;
    private final String referrerPolicy;

    public SecurityHeadersFilter(
            @Value("${app.security.headers.enabled:false}") boolean habilitado,
            @Value("${app.security.headers.content-security-policy:default-src 'none'}") String csp,
            @Value("${app.security.headers.hsts-max-age:31536000}") long hstsMaxAge,
            @Value("${app.security.headers.referrer-policy:no-referrer}") String referrerPolicy) {
        this.habilitado = habilitado;
        this.csp = csp;
        this.hstsMaxAge = hstsMaxAge;
        this.referrerPolicy = referrerPolicy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (habilitado) {
            response.setHeader("Content-Security-Policy", csp);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", referrerPolicy);
            // HSTS solo cuando la petición llega por HTTPS. Ponerlo sobre http
            // sería un aviso falso, y en local rompería el desarrollo.
            if (hstsMaxAge > 0 && esHttps(request)) {
                response.setHeader("Strict-Transport-Security",
                        "max-age=" + hstsMaxAge + "; includeSubDomains");
            }
            // Lo que el navegador puede guardar de una respuesta de error.
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }

    /**
     * ¿La petición llegó cifrada?
     *
     * <p>Se mira {@code X-Forwarded-Proto} porque, detrás de un proxy, el
     * conector de Tomcat solo ve HTTP: es el proxy quien sabe que por fuera era
     * HTTPS. Como {@code ForwardedHeaderFilter} reescribe la petición, también
     * sirve {@code request.isSecure()}.
     */
    private boolean esHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.toLowerCase(java.util.Locale.ROOT).startsWith("https");
    }
}