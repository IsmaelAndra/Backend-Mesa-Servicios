package com.mesaayuda.config;

import com.mesaayuda.service.SystemAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deja constancia de todo cambio hecho por la zona de administración.
 *
 * <p>Solo unas pocas operaciones de /admin registraban auditoría a mano (Graph,
 * IA, enrutamiento, workflows, MFA). El resto, que incluye SLA, plantillas de
 * notificación, branding, catálogos, equipos, configuración de sesión, chat,
 * páginas públicas y reportes SQL, cambiaba sin dejar rastro: se podía rebajar
 * un SLA o reescribir una plantilla de correo y nadie sabría quién lo hizo.
 *
 * <p>Se resuelve aquí, en un solo sitio, en lugar de añadir una llamada en cada
 * uno de los cuarenta métodos: así un endpoint nuevo queda auditado sin que nadie
 * tenga que acordarse. SystemAuditService ya toma de la petición el método, la
 * ruta, la IP y el identificador de petición, así que la fila dice exactamente
 * qué se tocó.
 *
 * <p>Solo se registran los cambios que terminaron bien. Los 403 los registra el
 * manejador de acceso denegado, y un 400 no cambió nada.
 */
@Component
public class AuditoriaAdministrativaInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAdministrativaInterceptor.class);

    private static final Set<String> METODOS_DE_LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * Rutas que ya registran su propia auditoría, con un nombre de acción y un
     * detalle más preciso. Registrarlas también aquí duplicaría la fila.
     */
    private static final List<String> YA_AUDITADAS = List.of(
            "PUT /admin/ai-config",
            "PUT /admin/microsoft-graph",
            "PUT /admin/routing-config",
            "POST /admin/daily-summary/send",
            "* /admin/workflows",
            "* /admin/mfa"
    );

    private final SystemAuditService systemAuditService;

    public AuditoriaAdministrativaInterceptor(SystemAuditService systemAuditService) {
        this.systemAuditService = systemAuditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String metodo = request.getMethod();
        if (metodo == null || METODOS_DE_LECTURA.contains(metodo.toUpperCase())) {
            return;
        }
        if (ex != null || response.getStatus() >= 400) {
            return;
        }
        String ruta = rutaSinContexto(request);
        if (yaAuditada(metodo.toUpperCase(), ruta)) {
            return;
        }
        try {
            systemAuditService.record(accion(metodo), entidad(ruta), primerId(ruta));
        } catch (Exception e) {
            // Auditar nunca debe convertir en error un cambio que ya se guardó.
            log.error("No se pudo auditar {} {}: {}", metodo, ruta, e.getMessage());
        }
    }

    static String accion(String metodo) {
        return switch (metodo.toUpperCase()) {
            case "POST" -> "ADMIN_CREATE";
            case "DELETE" -> "ADMIN_DELETE";
            default -> "ADMIN_UPDATE";
        };
    }

    /**
     * Recurso tocado, sin los identificadores: {@code /admin/sla/policies/5} da
     * {@code sla/policies}. La columna es de 50 caracteres.
     */
    static String entidad(String ruta) {
        List<String> partes = new ArrayList<>();
        for (String s : segmentos(ruta)) {
            if (!s.isEmpty() && !esNumero(s) && !esUuid(s)) {
                partes.add(s);
            }
        }
        String e = String.join("/", partes);
        return e.length() > 50 ? e.substring(0, 50) : e;
    }

    /** Primer identificador numérico de la ruta, o null si no hay. */
    static UUID primerId(String ruta) {
        for (String s : segmentos(ruta)) {
            if (esUuid(s)) {
                return UUID.fromString(s);
            }
        }
        return null;
    }

    static boolean yaAuditada(String metodo, String ruta) {
        for (String regla : YA_AUDITADAS) {
            int espacio = regla.indexOf(' ');
            String m = regla.substring(0, espacio);
            String prefijo = regla.substring(espacio + 1);
            boolean metodoCoincide = "*".equals(m) || m.equals(metodo);
            boolean rutaCoincide = ruta.equals(prefijo) || ruta.startsWith(prefijo + "/");
            if (metodoCoincide && rutaCoincide) {
                return true;
            }
        }
        return false;
    }

    private static List<String> segmentos(String ruta) {
        String sinAdmin = ruta.startsWith("/admin/") ? ruta.substring("/admin/".length()) : ruta;
        return List.of(sinAdmin.split("/"));
    }

    private static boolean esNumero(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private static boolean esUuid(String s) {
        return s != null && s.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private static String rutaSinContexto(HttpServletRequest request) {
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        String contexto = request.getContextPath() == null ? "" : request.getContextPath();
        return uri.startsWith(contexto) ? uri.substring(contexto.length()) : uri;
    }
}
