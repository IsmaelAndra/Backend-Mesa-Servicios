package com.mesaayuda.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * Únicas rutas permitidas mientras la contraseña está marcada como
     * "pendiente de cambio". Son justamente las que hacen falta para cumplir
     * esa obligación: cambiar la clave, renovar el token para poder hacerlo, y
     * cerrar sesión si el usuario decide no seguir.
     */
    private static final Set<String> RUTAS_PERMITIDAS_DURANTE_CAMBIO = Set.of(
            "/auth/change-password",
            "/auth/refresh",
            "/auth/logout"
    );

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromToken(token);
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException e) {
                // Cuenta eliminada o desactivada con un token todavía vigente. Antes
                // la excepción salía del filtro, fuera del alcance del manejador de
                // Spring Security, y el cliente recibía un 500. Se sigue como
                // anónimo y la regla de autorización responde 401.
                filterChain.doFilter(request, response);
                return;
            }

            if (emitidoAntesDelCorte(token, userDetails)) {
                // Token de una sesión cerrada (logout o cambio de contraseña). Se
                // trata como si no viniera: 401, y el interceptor del frontend
                // intentará renovar con su refresh token, que es quien decide si la
                // sesión sigue viva.
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (debeCambiarContrasena(userDetails) && !rutaPermitida(request)) {
                log.warn("Acceso bloqueado para {}: la contraseña inicial sigue sin cambiar ({}).",
                        username, request.getServletPath());
                escribirBloqueoCambioDeContrasena(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Si el token se emitió antes del corte de sesiones del usuario. El {@code iat}
     * del JWT tiene resolución de segundos, así que el corte se trunca igual: un
     * login hecho en el mismo segundo que el logout no debe salir rechazado.
     */
    private boolean emitidoAntesDelCorte(String token, UserDetails userDetails) {
        if (!(userDetails instanceof MesaUserDetails mesa) || mesa.getTokensValidAfter() == null) {
            return false;
        }
        Date emitido = tokenProvider.getIssuedAtFromToken(token);
        if (emitido == null) {
            return true;
        }
        long corte = mesa.getTokensValidAfter().getEpochSecond();
        return emitido.toInstant().getEpochSecond() < corte;
    }

    private boolean debeCambiarContrasena(UserDetails userDetails) {
        return userDetails instanceof MesaUserDetails mesa && mesa.isMustChangePassword();
    }

    private boolean rutaPermitida(HttpServletRequest request) {
        String path = request.getServletPath();
        return RUTAS_PERMITIDAS_DURANTE_CAMBIO.contains(path);
    }

    private void escribirBloqueoCambioDeContrasena(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                        + "\"message\":\"Debes cambiar tu contraseña inicial antes de continuar.\"}");
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}