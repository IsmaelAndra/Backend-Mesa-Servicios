package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.repository.WorkflowWaitRepository;
import com.mesaayuda.workflow.WorkflowActor;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Espera a que una persona decida.
 *
 * <p>Igual que el nodo de espera, no bloquea a nadie: deja la decisión pendiente
 * en {@code workflow_wait} y termina la ejecución en estado {@code APROBACION}.
 * Quien tiene el permiso decide desde la interfaz y otra ejecución continúa.
 *
 * <p><b>Siempre decide un SUPER_ADMIN.</b> El flujo es: lo que hace un ADMIN (o
 * cualquier persona) espera la firma del SUPER_ADMIN, y quien lo provocó ve el
 * resultado en "Mis solicitudes". Un grafo antiguo con {@code rol} ADMIN, AGENT
 * o USER se trata como SUPER_ADMIN: nunca baja el nivel de quien aprueba.
 */
@Component
public class ApprovalHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalHandler.class);

    private static final Set<String> ROLES_CONOCIDOS = Set.of("SUPER_ADMIN", "ADMIN", "AGENT", "USER");

    /** El único rol que decide aprobaciones. */
    public static final String ROL_APROBADOR = "SUPER_ADMIN";

    private final WorkflowWaitRepository waitRepository;
    private final WorkflowActor actor;
    private final com.mesaayuda.repository.UserRepository userRepository;
    private final com.mesaayuda.service.NotificationService notificationService;

    public ApprovalHandler(WorkflowWaitRepository waitRepository, WorkflowActor actor,
                           com.mesaayuda.repository.UserRepository userRepository,
                           com.mesaayuda.service.NotificationService notificationService) {
        this.waitRepository = waitRepository;
        this.actor = actor;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.APPROVAL;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();
        String rolSolicitado = config.get("rol") == null
                ? "" : String.valueOf(config.get("rol")).trim().toUpperCase(Locale.ROOT);

        if (!ROLES_CONOCIDOS.contains(rolSolicitado)) {
            return WorkflowResult.fallar("El nodo APPROVAL '" + nodo.id()
                    + "' pide la aprobación del rol '" + rolSolicitado + "', que no existe. "
                    + "Roles válidos: " + ROLES_CONOCIDOS + ".");
        }

        if (!ROL_APROBADOR.equals(rolSolicitado)) {
            log.info("El nodo APPROVAL '{}' pedía {}; las aprobaciones las decide siempre un {}.",
                    nodo.id(), rolSolicitado, ROL_APROBADOR);
            rolSolicitado = ROL_APROBADOR;
        }

        String ambito = contexto.leer("authorityScope") == null
                ? null : String.valueOf(contexto.leer("authorityScope"));
        if (ambito != null && !actor.ambitoValido(ambito)) {
            return WorkflowResult.fallar("El nodo APPROVAL '" + nodo.id()
                    + "' no puede ejecutarse: el ambito del workflow no es valido.");
        }
        // Pedir la firma de alguien superior no da permisos al proceso: el motor
        // sigue ejecutando con su ámbito. Por eso no se compara con él.

        // Aprobador concreto (opcional). Tiene que tener al menos el rol pedido:
        // si no, el nodo serviría para saltarse el control por rol.
        java.util.UUID aprobadorIdCfg = contexto.configUuid(nodo, "usuarioId");
        java.util.UUID aprobadorId = null;
        if (aprobadorIdCfg != null) {
            aprobadorId = aprobadorIdCfg;
        }
        com.mesaayuda.entity.User aprobador = null;
        if (aprobadorId != null) {
            aprobador = userRepository.findById(aprobadorId)
                    .filter(u -> !Boolean.TRUE.equals(u.getDeletedUser()) && "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                    .orElse(null);
            if (aprobador == null) {
                return WorkflowResult.fallar("El aprobador indicado en '" + nodo.id() + "' no existe o no está activo.");
            }
            String rolAprobador = aprobador.getRolUser() == null ? "" : aprobador.getRolUser().getNameRole();
            if (!ROL_APROBADOR.equals(rolAprobador)) {
                return WorkflowResult.fallar("El aprobador indicado en '" + nodo.id() + "' es " + rolAprobador
                        + " y las aprobaciones solo las decide un " + ROL_APROBADOR + ".");
            }
        }

        OffsetDateTime limite = limite(config);
        WorkflowWait espera = new WorkflowWait();
        espera.setExecutionExecution(contexto.getExecutionId());
        espera.setNodeIdWait(nodo.id());
        espera.setKindWait(WorkflowWait.TIPO_APPROVAL);
        espera.setApproverRoleWait(rolSolicitado);
        espera.setApproverUserWait(aprobadorId);
        espera.setStatusWait(WorkflowWait.ESTADO_PENDIENTE);
        espera.setResumeAtWait(limite);
        espera.setCreatedAtWait(OffsetDateTime.now());
        waitRepository.save(espera);

        // Lo que se pide, con las variables ya resueltas: es lo que verá quien
        // tenga que decidir en su bandeja.
        String solicitud = contexto.configTexto(nodo, "mensaje");
        if (solicitud == null) {
            solicitud = nodo.name() == null ? "Aprobación pendiente" : nodo.name();
        }
        contexto.resultado(nodo, "solicitud", solicitud);
        contexto.resultado(nodo, "rol", rolSolicitado);
        avisar(aprobador, rolSolicitado, solicitud);

        log.info("El nodo APPROVAL '{}' deja la ejecución {} pendiente de un {}",
                nodo.id(), contexto.getExecutionId(), rolSolicitado);
        return WorkflowResult.parar(WorkflowExecution.ESTADO_APROBACION,
                "A la espera de que un " + rolSolicitado + " decida.");
    }

    /** Aviso en la campana a quien puede decidir: el aprobador indicado o quienes tienen el rol. */
    private void avisar(com.mesaayuda.entity.User aprobador, String rol, String solicitud) {
        java.util.List<com.mesaayuda.entity.User> destinatarios = aprobador != null
                ? java.util.List.of(aprobador) : userRepository.findByRoleName(rol);
        for (com.mesaayuda.entity.User u : destinatarios) {
            try {
                notificationService.sendNotification(null, u, TIPO_NOTIFICACION,
                        "Aprobación pendiente", solicitud + " Revísela en la bandeja de aprobaciones.");
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar de la aprobación a {}: {}", u.getCorporateEmailUser(), e.getMessage());
            }
        }
    }

    /** Tipo de notificación de las solicitudes de aprobación. */
    public static final String TIPO_NOTIFICACION = "WORKFLOW_APROBACION";

    private OffsetDateTime limite(Map<String, Object> config) {
        Object horas = config.get("horas_limite");
        long h = 0;
        if (horas instanceof Number n) {
            h = n.longValue();
        } else if (horas != null) {
            try {
                h = Long.parseLong(String.valueOf(horas).trim());
            } catch (NumberFormatException e) {
                h = 0;
            }
        }
        // Sin límite declarado no caduca: una aprobación no vencida nunca debería
        // desaparecer sola. Si hay un plazo, se respeta.
        return h > 0 ? OffsetDateTime.now().plusHours(h) : null;
    }

    public static int rango(String rol) {
        String normalizado = rol == null ? "" : rol.trim().toUpperCase(Locale.ROOT);
        return switch (normalizado) {
            case "SUPER_ADMIN" -> 4;
            case "ADMIN" -> 3;
            case "AGENT" -> 2;
            case "USER" -> 1;
            default -> 0;
        };
    }
}