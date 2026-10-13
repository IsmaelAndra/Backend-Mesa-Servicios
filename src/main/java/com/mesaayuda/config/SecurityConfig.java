package com.mesaayuda.config;

import com.mesaayuda.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Lazy: la configuración de seguridad se construye antes que la persistencia. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.mesaayuda.service.SystemAuditService systemAuditService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String origenes) throws Exception {
        http
            // CSRF de Spring se desactiva a proposito, y conviene no tocar esta
            // linea sin entender lo de abajo. La API se autentica con un token de
            // acceso en la cabecera Authorization, que el navegador no adjunta solo
            // al hacer peticiones cross-site, asi que un forgery clasico no aplica.
            //
            // Lo unico que si usa cookie es el refresh token, en /auth/refresh y
            // /auth/logout, y ahi lo protege RefreshTokenCookie.csrfValido(), que
            // exige el doble envio (cookie legible + cabecera X-XSRF-TOKEN). Sin
            // esa comprobacion, cualquier pagina podria renovar la sesion del
            // usuario usando sus cookies.
            //
            // Si se vuelve a activar el CSRF de Spring tal cual, rompe el login:
            // /auth/login es publico y no lleva token de donde sacarlo, asi que
            // todos los POST de entrada empezarían a devolver 403.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource(origenes)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // /auth/refresh y /auth/logout tienen que ser publicas aunque el
                // access token ya haya caducado: si exigieran un token vigente,
                // el interceptor no podria renovar la sesion justo cuando mas la
                // necesita, y el usuario acabaria expulsado. El refresh token es
                // opaco, se guarda con hash, es de un solo uso (rota al usarlo)
                // y se valida contra la base, asi que no depende del access token.
                // /auth/reset-password tambien es publica: es a donde lleva el enlace del
                // correo, y quien lo abre todavia no puede iniciar sesion. Si
                // exigiera token, el enlace solo funcionaria para quien ya tiene
                // sesion, que es justo a quien no le hace falta.
                .requestMatchers("/auth/login", "/auth/register", "/auth/forgot-password",
                                 "/auth/reset-password", "/auth/refresh", "/auth/logout",
                                 "/auth/mfa/confirm").permitAll()
                .requestMatchers(HttpMethod.GET, "/admin/catalog").permitAll()
                .requestMatchers(HttpMethod.GET, "/admin/branding").permitAll()
                // Swagger queda en permitAll a propósito: es una herramienta de desarrollo y
                // que exija sesión solo estorba. Lo que lo protege es que está apagado por
                // defecto (springdoc.*.enabled en application.yml) y solo lo encienden el
                // perfil "dev" o SWAGGER_ENABLED=true. Apagado, estas rutas responden 404.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Solo los recursos de marca (logo, icono del sistema, fondos del
                // chat, logos de informe y avatares) son publicos: se pintan en la
                // pantalla de acceso, antes de que exista sesion, y ademas los
                // descarga el propio servidor al generar los PDF.
                //
                // Los adjuntos de los tickets NO entran por aqui. Antes toda la
                // carpeta estaba en permitAll y servida como recurso estatico, con
                // lo que qualquer URL permitia descargar el archivo de un ticket
                // ajeno. Ahora viven en otra zona, no la publica ningun handler de
                // recursos y se descargan por AttachmentController, que exige sesion
                // y permiso de lectura sobre ese ticket.
                .requestMatchers(HttpMethod.GET, "/uploads/" + UploadZones.PUBLICO + "/**").permitAll()
                // Imagenes de las plantillas de notificacion, guardadas en la base.
                // El HTML del correo lo abre el cliente de correo del destinatario
                // desde su propio servidor, sin sesion ni token de la aplicacion, asi
                // que la URL tiene que ser alcanzable sin autenticacion. A cambio
                // lleva un token aleatorio de 32 hex, no el id de la fila, que seria
                // correlativo y se podria recorrer para inventariar todas las
                // imagenes subidas por los administradores.
                .requestMatchers(HttpMethod.GET, "/templates/image/*").permitAll()
                // Solo la apertura del canal es publica: EventSource no envia
                // cabeceras y se autentica con un ticket de un solo uso que el
                // propio SseController valida. Pedir ese ticket (POST
                // /events/ticket) exige sesion, por la regla general de abajo.
                .requestMatchers(HttpMethod.GET, "/events").permitAll()
                  // Textos de las pantallas de error y politica de privacidad.
                  // Son publicos por necesidad, no por descuido: la pantalla de un
                  // 403 o de un 500 aparece justamente cuando no hay sesion valida,
                  // y el frontend necesita el texto para explicar que ocurrio en
                  // lugar de quedarse en blanco. La escritura sigue protegida por
                  // "/admin/**" y, dentro, por PreAuthorize de super administrador.
                  .requestMatchers(HttpMethod.GET, "/pages/public/**").permitAll()
                // El chat necesita saber si puede pintar el doble check, y lo
                // consulta cualquier usuario con sesion. Va antes de /admin/**
                // porque el filtro de URL de abajo exige administrador para todo
                // lo que empiece por /admin; sin esta linea, el endpoint de solo
                // lectura quedaria restringido y el chat no tendria acuses.
                .requestMatchers("/admin/chat-config/mi-visibilidad").authenticated()
                .requestMatchers("/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                // Las comprobaciones de rol de estos controladores siguen en su
                // sitio; estas reglas las adelantan al filtro. Sin ellas, Spring
                // valida el cuerpo y los parámetros antes de llegar al chequeo, y un
                // USER recibía un 400 con los mensajes de validación (o un 500 si
                // faltaba un parámetro) en lugar de un 403.
                .requestMatchers("/users/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/users/agents")
                        .hasAnyAuthority("ROLE_AGENT", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                .requestMatchers("/users", "/users/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                .requestMatchers("/workflows/aprobaciones", "/workflows/aprobaciones/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                .requestMatchers("/calendar/**", "/tasks/**", "/quick-replies", "/quick-replies/**")
                        .hasAnyAuthority("ROLE_AGENT", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                .anyRequest().authenticated()
            )
.exceptionHandling(ex -> ex
                 .authenticationEntryPoint((request, response, authException) -> {
                     response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                     response.setContentType("application/json");
                     response.getWriter().write("{\"message\":\"No autenticado\"}");
                 })
                 .accessDeniedHandler((request, response, accessDeniedException) -> {
                     // Un 403 no es solo un "no": es alguien que intentó algo que
                     // no le corresponde. Sin registrarlo, un ADMIN que va probando
                     // endpoints de super administrador deja cero rastro, y eso es
                     // justo el patrón que hay que poder ver.
                     registrarAccesoDenegado(request);
                     response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                     response.setContentType("application/json");
                     response.getWriter().write("{\"message\":\"Acceso denegado\"}");
                 })
             )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Deja constancia de un acceso denegado.
     *
     * <p>Se escribe a mano y no a través de SystemAuditService a propósito: este
     * código se ejecuta cuando Spring Security ya ha denegado la petición, y en
     * muchos casos por un fallo de autorización dentro del propio servicio. Si
     * el registro dependiera del mismo camino denegado, un fallo al auditar podría
     * traducirse en un 500 en lugar de un 403, que es peor: dejaría de responder
     * lo que pasó en realidad.
     *
     * <p>Solo se guardan intento y origen, nunca el cuerpo de la petición ni sus
     * cabeceras: aquí puede llegar una contraseña o un token, y una tabla de
     * auditoría no es el sitio adecuado para eso.
     */
    private void registrarAccesoDenegado(HttpServletRequest request) {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            String quien = auth == null || !auth.isAuthenticated()
                    ? "anonimo"
                    : (auth.getName() == null ? "desconocido" : auth.getName());
            String rol = auth == null || auth.getAuthorities() == null
                    ? ""
                    : auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            Object ip = request.getAttribute(com.mesaayuda.security.ClientIpFilter.ATRIBUTO_IP);
            StringBuilder linea = new StringBuilder("Acceso denegado: ")
                    .append(request.getMethod()).append(' ').append(request.getRequestURI());
            if (ip != null) {
                linea.append(" desde ").append(ip);
            }
            linea.append(" usuario=").append(quien);
            if (!rol.isEmpty()) {
                linea.append(" rol=").append(rol);
            }
            // WARN a propósito: un 403 aislado es ruido, pero un usuario que los
            // repite está buscando algo, y eso tiene que destacar en el log.
            log.warn(linea.toString());
            // Y a audit_log, que es de donde lee la detección de actividad
            // sospechosa. Va dentro del try: si falla, el 403 sale igual.
            systemAuditService.record("ACCESS_DENIED", "Endpoint", null, quien, rol.isEmpty() ? null : rol);
        } catch (Exception e) {
            // Nunca debe romper la respuesta del 403.
            log.debug("No se pudo registrar el acceso denegado: {}", e.getMessage());
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String origenes) {
        CorsConfiguration configuration = new CorsConfiguration();
        // Antes esta lista estaba fija en http://localhost:* y la propiedad
        // app.cors.allowed-origins (que se define en application.yml y se
        // alimenta de CORS_ORIGINS) no se leia en ningun sitio. Resultado:
        // cambiar la variable de entorno no cambiaba nada y cualquier
        // despliegue con otro origen quedaba bloqueado por el navegador.
        List<String> patrones = new ArrayList<>();
        for (String origen : origenes.split(",")) {
            String limpio = origen.trim();
            if (!limpio.isEmpty()) {
                patrones.add(limpio);
            }
        }
        if (patrones.isEmpty()) {
            // Sin origins configurados se cae al origen de desarrollo, que es lo
            // que hay documentado en .env.example.
            patrones.add("http://localhost:4200");
            log.warn("No hay origins en app.cors.allowed-origins; se usa {} por defecto.", patrones);
        }
        configuration.setAllowedOriginPatterns(patrones);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
