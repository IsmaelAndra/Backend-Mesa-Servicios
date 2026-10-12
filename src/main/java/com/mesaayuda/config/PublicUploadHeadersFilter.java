package com.mesaayuda.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeceras de seguridad para los recursos de la zona publica.
 *
 * <p>Esas imágenes se sirven sin sesión porque se muestran en la pantalla de
 * acceso, y en producción el frontend y la API comparten origen: la URL es
 * {@code /api/uploads/publico/...}. Es decir, un archivo que se depositara ahí
 * se ejecutaría en el propio origen de la aplicación, que es donde la SPA tiene
 * el token de sesión.
 *
 * <p>Por eso el alta de estas imágenes ya exige que el contenido sea de verdad
 * una imagen de mapa de bits y rechaza los vectoriales. Este filtro es la capa
 * que protege lo que ya había: la carpeta contiene SVG subidos antes de que
 * existiera esa validación, dos de ellos referenciados desde la configuración
 * vigente (la imagen de carga y el icono del sistema), y borrarlos de un plumazo
 * rompería la pantalla de inicio.
 *
 * <p>Una directiva {@code default-src 'none'} sobre la respuesta impide que el
 * SVG ejecute su script aunque el archivo lo contenga: el navegador no le da
 * permiso para nada. Es la defensa que no obliga a tocar los recursos que la
 * institución ya configuró.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PublicUploadHeadersFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "/uploads/" + UploadZones.PUBLICO + "/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI() != null && request.getRequestURI().contains(PREFIJO)) {
            // No hay CSP en el resto de la API: el backend no sirve HTML de
            // aplicacion, solo Swagger y el HTML de los correos. Aqui si importa
            // porque se sirven byteschosen por el usuario desde el origen de la app.
            response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
            response.setHeader("X-Content-Type-Options", "nosniff");
            // Que el navegador decida con la extension real y no con lo que le
            // sugiera el servidor: evita que un .svg se interprete como otra cosa
            // y al reves.
            response.setHeader("X-Frame-Options", "DENY");
        }
        filterChain.doFilter(request, response);
    }
}