package com.mesaayuda.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Decide de dónde sale la IP del cliente, y deja constancia cuando lo odd.
 *
 * <p>El problema que corrige: {@code AuthService.origenActual()} leia
 * {@code X-Forwarded-For} a puñetazo, sin comprobar quien lo manda. Como los
 * limites de {@code login} y {@code forgot-password} se cuentan por origen, un
 * atacante que enviara la cabecera con un valor distinto en cada peticion
 * esquivaba el limite por completo: cada intento pareceria venir de una IP nueva.
 * En la practica, el limite por IP no existia.
 *
 * <p>La regla ahora es la contraria y deliberadamente mas (->escrita):
 *
 * <ul>
 *   <li>Sin proxy delante ({@code app.proxy.trust-forwarded-headers=false}, que es
 *       lo de desarrollo), la IP es {@code getRemoteAddr()} y punto. La cabecera
 *       se ignora por completo aunque venga.
 *   <li>Con proxy delante (propiedad a true), se registra un
 *       {@code ForwardedHeaderFilter} que reescribe la peticion y hace que
 *       {@code getRemoteAddr()} devuelva el cliente real. Es el unico camino por
 *       el que una cabecera de reenvio puede influir.
 * </ul>
 *
 * <p>Que el filtro este o no activo no se adivina: se registra en el arranque, y
 * si llega una cabecera de reenvio sin filtro se avisa por log. Un despliegue mal
 * configurado se ve en el log en vez de descubrirse cuando todos los limites por
 * IP risulten inútiles.
 */
@Component
public class ClientIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClientIpFilter.class);

    /**
     * Cabeceras de reenvio. Se listan todas porque no hay una sola forma de
     * desplegar esto: Nginx/IIS usan X-Forwarded-For, y los balanceadores de nube
     * añaden X-Real-IP o las variantes estandarizadas.
     */
    private static final String[] CABECERAS_REENVIO = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Forwarded"
    };

    private final boolean confiarEnCabeceras;

    public ClientIpFilter(
            @Value("${app.proxy.trust-forwarded-headers:false}") boolean confiarEnCabeceras) {
        this.confiarEnCabeceras = confiarEnCabeceras;
        if (confiarEnCabeceras) {
            log.warn("Se confia en las cabeceras de reenvio (X-Forwarded-For y similares). "
                    + "La IP real solo es fiable si TODAS las peticiones entran por el proxy. "
                    + "Si el puerto del backend esta expuesto a internet, cualquiera puede "
                    + "inventarse la IP y saltarse los limites por origen.");
        } else {
            log.info("No se confia en las cabeceras de reenvio: la IP de los limites sera "
                    + "la conexion directa. Correcto en desarrollo; en produccion con "
                    + "proxy hay que poner app.proxy.trust-forwarded-headers=true.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!confiarEnCabeceras && traeCabeceraDeReenvio(request)) {
            log.debug("Cabecera de reenvio ignorada (no hay proxy declarado): {} {}",
                    request.getMethod(), request.getRequestURI());
        }
        // Se pone la IP ya resuelta como atributo de peticion para que todos los
        // consumidores lean el mismo sitio, en vez de que cada uno se monte su
        // propio metodo de averiguarla.
        request.setAttribute(ATRIBUTO_IP, ipDe(request));
        chain.doFilter(request, response);
    }

    /** Atributo de peticion con la IP ya resuelta. */
    public static final String ATRIBUTO_IP = ClientIpFilter.class.getName() + ".ip";

    /**
     * IP efectiva del cliente.
     *
     * <p>Con el {@code ForwardedHeaderFilter} activo, {@code getRemoteAddr()} ya
     * devuelve el cliente real porque el filtro reescribio la peticion. Por eso no
     * se parsea la cabecera aqui: leerla seria volver a confiar en ella, que es
     * justo lo que se ha eliminado.
     */
    private String ipDe(HttpServletRequest request) {
        String remota = request.getRemoteAddr();
        return remota == null || remota.isBlank() ? "desconocido" : remota;
    }

    private boolean traeCabeceraDeReenvio(HttpServletRequest request) {
        for (String cabecera : CABECERAS_REENVIO) {
            if (request.getHeader(cabecera) != null) {
                return true;
            }
        }
        return false;
    }
}