package com.mesaayuda.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * El auditor consulta todo y no cambia nada.
 *
 * <p>Se frena en un solo sitio, por método HTTP, en lugar de repartir el "no"
 * por cada controlador: un endpoint nuevo de escritura queda cerrado para el
 * auditor sin que nadie tenga que acordarse. Solo pasan sus propias cosas de
 * sesión y cuenta (contraseña, perfil, MFA), el canal de eventos, marcar sus
 * avisos como leídos y el chat con MAPI.
 */
public class AuditorSoloLecturaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditorSoloLecturaFilter.class);

    private static final Set<String> METODOS_LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    /** Escrituras propias del auditor: su sesión, su cuenta y sus avisos. */
    private static final List<String> PERMITIDAS = List.of(
            "/auth", "/users/me", "/events/ticket", "/notifications", "/ai/chat");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (esAuditor() && !METODOS_LECTURA.contains(request.getMethod()) && !permitida(request.getServletPath())) {
            log.warn("Escritura bloqueada para el auditor {}: {} {}",
                    SecurityContextHolder.getContext().getAuthentication().getName(),
                    request.getMethod(), request.getServletPath());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"El auditor solo puede consultar\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean permitida(String ruta) {
        if (ruta == null) {
            return false;
        }
        // Pedir acceso de emergencia a un confidencial: es lo único que el
        // auditor escribe sobre un ticket, y es justo para poder leerlo.
        if (ruta.matches("/tickets/[^/]+/acceso-confidencial")) {
            return true;
        }
        // Consulta de plazos del tablero: va por POST solo por el tamaño de la lista de ids.
        if (ruta.equals("/tickets/sla/plazos")) {
            return true;
        }
        // La ruta exacta o algo debajo de ella ("/notifications/5/read"), nunca
        // un prefijo suelto: "/authx" no es "/auth".
        return PERMITIDAS.stream().anyMatch(p -> ruta.equals(p) || ruta.startsWith(p + "/"));
    }

    private static boolean esAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_AUDITOR".equals(a.getAuthority()));
    }
}
