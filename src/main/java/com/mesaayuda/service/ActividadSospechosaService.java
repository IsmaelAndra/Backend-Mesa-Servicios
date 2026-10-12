package com.mesaayuda.service;

import com.mesaayuda.entity.User;
import com.mesaayuda.repository.AuditLogRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Detección básica de actividad sospechosa sobre audit_log.
 *
 * <p>No es un SIEM: no correlaciona fuentes ni aprende patrones. Cuenta eventos
 * que ya se registran (logins fallidos, bloqueos, accesos denegados, cambios
 * administrativos, reutilización de refresh token) en una ventana corta y, si
 * alguno pasa de su umbral, deja una fila SUSPICIOUS_ACTIVITY y avisa a los
 * super administradores. Antes todos esos eventos se guardaban, pero nadie los
 * miraba hasta que alguien iba a buscarlos.
 *
 * <p>Una misma alerta no se repite durante {@link #SILENCIO}: quien está
 * atacando va a seguir, y un aviso cada cinco minutos solo enseña a ignorarlos.
 */
@Service
public class ActividadSospechosaService {

    private static final Logger log = LoggerFactory.getLogger(ActividadSospechosaService.class);

    static final String ACCION = "SUSPICIOUS_ACTIVITY";
    static final String TIPO_NOTIFICACION = "ALERTA_SEGURIDAD";
    static final Duration SILENCIO = Duration.ofMinutes(60);

    private static final List<String> CAMBIOS_ADMINISTRATIVOS = List.of(
            "ADMIN_CREATE", "ADMIN_UPDATE", "ADMIN_DELETE",
            "USER_CREATE", "USER_UPDATE", "USER_DELETE", "USER_STATUS_CHANGE",
            "MSGRAPH_CONFIG", "AI_CONFIG", "ROUTING_CONFIG", "MFA_SETTING",
            "WORKFLOW_CREATE", "WORKFLOW_UPDATE", "WORKFLOW_DELETE",
            "WORKFLOW_ACTIVATE", "WORKFLOW_DEACTIVATE");

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private NotificationService notificationService;

    @Value("${app.security.suspicious.window-minutes:10}")
    private long ventanaMinutos = 10;

    @Value("${app.security.suspicious.failed-logins-per-ip:10}")
    private long loginsFallidosPorIp = 10;

    @Value("${app.security.suspicious.access-denied-per-user:10}")
    private long accesosDenegadosPorUsuario = 10;

    @Value("${app.security.suspicious.admin-changes-per-user:30}")
    private long cambiosAdministrativosPorUsuario = 30;

    /** Revisa la ventana reciente y avisa de lo que pase de los umbrales. Devuelve las alertas nuevas. */
    public List<String> revisar() {
        OffsetDateTime desde = OffsetDateTime.now().minusMinutes(ventanaMinutos);
        List<String> nuevas = new ArrayList<>();

        for (Object[] fila : auditLogRepository.contarPorIp(List.of("LOGIN_FAILED"), desde, loginsFallidosPorIp)) {
            alertar(nuevas, "LOGIN_FAILED_IP|" + fila[0],
                    fila[1] + " inicios de sesión fallidos desde la IP " + fila[0]);
        }
        for (Object[] fila : auditLogRepository.contarPorUsuario(List.of("LOGIN_BLOCKED"), desde, 1)) {
            alertar(nuevas, "LOGIN_BLOCKED|" + fila[0],
                    "Cuenta " + fila[0] + " bloqueada por intentos fallidos de inicio de sesión");
        }
        for (Object[] fila : auditLogRepository.contarPorUsuario(List.of("ACCESS_DENIED"), desde,
                accesosDenegadosPorUsuario)) {
            alertar(nuevas, "ACCESS_DENIED|" + fila[0],
                    fila[1] + " accesos denegados para " + fila[0] + " (posible barrido de permisos o ids)");
        }
        for (Object[] fila : auditLogRepository.contarPorUsuario(CAMBIOS_ADMINISTRATIVOS, desde,
                cambiosAdministrativosPorUsuario)) {
            alertar(nuevas, "ADMIN_CHANGES|" + fila[0],
                    fila[1] + " cambios administrativos de " + fila[0] + " en " + ventanaMinutos + " min");
        }
        for (Object[] fila : auditLogRepository.contarPorEntidad(List.of("REFRESH_TOKEN_REUSE"), desde, 1)) {
            alertar(nuevas, "REFRESH_TOKEN_REUSE|" + fila[0],
                    "Reutilización de un refresh token del usuario #" + fila[0]
                            + ": posible robo de sesión, se cerraron sus sesiones");
        }
        return nuevas;
    }

    private void alertar(List<String> nuevas, String clave, String descripcion) {
        // La clave va al principio del detalle y es lo que se compara para no
        // repetir el aviso; el resto del texto lleva el recuento, que cambia.
        String prefijo = "clave=" + clave + ";";
        if (auditLogRepository.existsByActionAndDetailsStartingWithAndCreatedAtAfter(
                ACCION, prefijo, OffsetDateTime.now().minus(SILENCIO))) {
            return;
        }
        String detalle = prefijo + " " + descripcion;
        systemAuditService.record(ACCION, "Seguridad", null, "sistema",
                detalle.length() > 1000 ? detalle.substring(0, 1000) : detalle);
        log.warn("Actividad sospechosa: {}", descripcion);
        nuevas.add(descripcion);

        List<User> destinatarios = userRepository
                .findByRolUserNameRoleAndStatusUserAndDeletedUserFalseOrderByCreatedAtUserAsc("SUPER_ADMIN", "ACTIVE");
        for (User superAdmin : destinatarios) {
            try {
                notificationService.sendNotification(null, superAdmin, TIPO_NOTIFICACION,
                        "Alerta de seguridad", descripcion);
            } catch (Exception e) {
                // Un fallo al avisar a uno no debe dejar sin aviso a los demás.
                log.error("No se pudo avisar a {} de la alerta de seguridad: {}",
                        superAdmin.getCorporateEmailUser(), e.getMessage());
            }
        }
    }
}
