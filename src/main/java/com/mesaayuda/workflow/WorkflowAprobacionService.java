package com.mesaayuda.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.dto.WorkflowAprobacionResponse;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowWaitRepository;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.TicketAccessPolicy;
import com.mesaayuda.service.UserService;
import com.mesaayuda.workflow.handler.ApprovalHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bandeja y decisión de las aprobaciones de workflow.
 *
 * <p>Antes el nodo APPROVAL creaba la espera y no había forma de decidirla: no
 * existía endpoint, así que toda ejecución con aprobación se quedaba pendiente
 * para siempre.
 *
 * <p><b>Quién decide.</b> Si el nodo indica un aprobador concreto, solo esa
 * persona. Si no, cualquiera con un rol igual o superior al pedido (un ADMIN
 * puede decidir lo que se pide a un AGENT; un AGENT no puede decidir lo que se
 * pide a un ADMIN). La comprobación se hace al decidir, con el rol de ese
 * momento: si a alguien le bajan el rol, pierde la aprobación aunque estuviera
 * en su bandeja.
 */
@Service
public class WorkflowAprobacionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAprobacionService.class);

    private final WorkflowWaitRepository waitRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;
    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final TicketAccessPolicy accessPolicy;
    private final SystemAuditService systemAuditService;
    private final ObjectMapper objectMapper;

    public WorkflowAprobacionService(WorkflowWaitRepository waitRepository,
                                     WorkflowExecutionRepository executionRepository,
                                     WorkflowRepository workflowRepository,
                                     TicketRepository ticketRepository,
                                     UserService userService,
                                     TicketAccessPolicy accessPolicy,
                                     SystemAuditService systemAuditService,
                                     ObjectMapper objectMapper) {
        this.waitRepository = waitRepository;
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
        this.ticketRepository = ticketRepository;
        this.userService = userService;
        this.accessPolicy = accessPolicy;
        this.systemAuditService = systemAuditService;
        this.objectMapper = objectMapper;
    }

    /** Las aprobaciones pendientes que el usuario actual puede decidir. */
    @Transactional(readOnly = true)
    public List<WorkflowAprobacionResponse> pendientes() {
        User actual = userService.getCurrentUser();
        return waitRepository.aprobacionesPendientes().stream()
                .filter(w -> puedeDecidir(actual, w))
                .map(w -> respuesta(w, actual))
                .toList();
    }

    /** Cuántas tiene pendientes, para el contador del menú. */
    @Transactional(readOnly = true)
    public long contarPendientes() {
        User actual = userService.getCurrentUser();
        return waitRepository.aprobacionesPendientes().stream().filter(w -> puedeDecidir(actual, w)).count();
    }

    /**
     * Aprueba o rechaza. La ejecución vuelve a la cola y sigue por la rama
     * APROBADO o RECHAZADO; si no tiene esa rama, termina.
     */
    @Transactional
    public void decidir(UUID esperaId, boolean aprobar, String comentario) {
        User actual = userService.getCurrentUser();
        WorkflowWait espera = waitRepository.findById(esperaId)
                .filter(w -> WorkflowWait.TIPO_APPROVAL.equals(w.getKindWait()))
                .orElseThrow(() -> new ResourceNotFoundException("Aprobación", "id", esperaId));
        if (!puedeDecidir(actual, espera)) {
            log.warn("Acceso denegado: {} intentó decidir la aprobación {} (pide {}).",
                    actual.getCorporateEmailUser(), esperaId, espera.getApproverRoleWait());
            throw new AccessDeniedException("No puede decidir esta aprobación");
        }
        String limpio = comentario == null ? null : comentario.trim();
        if (limpio != null && limpio.length() > 500) {
            limpio = limpio.substring(0, 500);
        }
        if (!aprobar && (limpio == null || limpio.isEmpty())) {
            // Un rechazo sin motivo no le sirve a nadie para corregir la solicitud.
            throw new BadRequestException("Indique el motivo del rechazo.");
        }
        int cerradas = waitRepository.decidir(esperaId,
                aprobar ? WorkflowWait.ESTADO_APROBADA : WorkflowWait.ESTADO_RECHAZADA,
                actual.getIdUser(), OffsetDateTime.now(), limpio);
        if (cerradas == 0) {
            throw new BadRequestException("Esta aprobación ya se decidió o venció.");
        }

        String puerto = aprobar ? WorkflowExecution.PUERTO_APROBADO : WorkflowExecution.PUERTO_RECHAZADO;
        WorkflowExecution ejecucion = executionRepository.findById(espera.getExecutionExecution()).orElse(null);
        if (ejecucion != null && WorkflowExecution.ESTADO_APROBACION.equals(ejecucion.getStatusExecution())) {
            ejecucion.setStatusExecution(WorkflowExecution.ESTADO_PENDIENTE);
            ejecucion.setResumePortExecution(puerto);
            ejecucion.setVariablesExecution(conDecision(ejecucion.getVariablesExecution(), espera.getNodeIdWait(),
                    puerto, limpio, actual));
            executionRepository.save(ejecucion);
        }
        systemAuditService.record("WORKFLOW_APPROVAL_DECIDED", "WorkflowExecution",
                espera.getExecutionExecution(), actual.getCorporateEmailUser(),
                puerto + " en el nodo '" + espera.getNodeIdWait() + "'" + (limpio == null ? "" : ": " + limpio));
        log.info("{} {} la aprobación {} (ejecución {})", actual.getCorporateEmailUser(),
                aprobar ? "aprobó" : "rechazó", esperaId, espera.getExecutionExecution());
    }

    /** Si el usuario puede decidir esta aprobación. Paquete para poder probarlo. */
    boolean puedeDecidir(User usuario, WorkflowWait espera) {
        if (usuario == null || Boolean.TRUE.equals(usuario.getDeletedUser())
                || !"ACTIVE".equalsIgnoreCase(usuario.getStatusUser())) {
            return false;
        }
        if (espera.getApproverUserWait() != null) {
            return espera.getApproverUserWait().equals(usuario.getIdUser());
        }
        String rol = usuario.getRolUser() == null ? null : usuario.getRolUser().getNameRole();
        int pedido = ApprovalHandler.rango(espera.getApproverRoleWait());
        return pedido > 0 && ApprovalHandler.rango(rol) >= pedido;
    }

    private WorkflowAprobacionResponse respuesta(WorkflowWait espera, User actual) {
        WorkflowExecution ejecucion = executionRepository.findById(espera.getExecutionExecution()).orElse(null);
        String nombreWorkflow = ejecucion == null ? null
                : workflowRepository.findById(ejecucion.getWorkflowWorkflow()).map(w -> w.getNameWorkflow()).orElse(null);
        String solicitud = solicitud(ejecucion, espera.getNodeIdWait());
        String tipo = ejecucion == null ? null : ejecucion.getEntityTypeExecution();
        UUID id = ejecucion == null ? null : ejecucion.getEntityIdExecution();
        String etiqueta = null;
        if ("TICKET".equals(tipo) && id != null) {
            // El título solo si quien decide puede leer ese ticket: una aprobación
            // pedida a un USER no debe enseñarle tickets de otras personas.
            Ticket t = ticketRepository.findById(id).orElse(null);
            if (t != null && accessPolicy.puedeLeer(t, actual)) {
                etiqueta = "#" + t.getNumberTicket() + " " + t.getTitleTicket();
            }
        }
        return new WorkflowAprobacionResponse(espera.getIdWait(), espera.getExecutionExecution(), nombreWorkflow,
                espera.getNodeIdWait(), solicitud, espera.getApproverRoleWait(), tipo, id, etiqueta,
                espera.getCreatedAtWait(), espera.getResumeAtWait());
    }

    /** El texto que dejó el nodo APPROVAL en nodos.<id>.solicitud. */
    @SuppressWarnings("unchecked")
    private String solicitud(WorkflowExecution ejecucion, String nodoId) {
        if (ejecucion == null || ejecucion.getVariablesExecution() == null) {
            return null;
        }
        try {
            Map<String, Object> vars = objectMapper.readValue(ejecucion.getVariablesExecution(), Map.class);
            Object nodos = vars.get("nodos");
            if (nodos instanceof Map<?, ?> mapa && mapa.get(nodoId) instanceof Map<?, ?> nodo) {
                Object s = nodo.get("solicitud");
                return s == null ? null : String.valueOf(s);
            }
        } catch (Exception e) {
            log.debug("No se pudo leer la solicitud de la ejecución {}: {}", ejecucion.getIdWorkflowExecution(),
                    e.getMessage());
        }
        return null;
    }

    /** Deja la decisión en nodos.<id>.* para que los nodos siguientes puedan usarla. */
    @SuppressWarnings("unchecked")
    private String conDecision(String variablesJson, String nodoId, String puerto, String comentario, User quien) {
        try {
            Map<String, Object> vars = variablesJson == null || variablesJson.isBlank()
                    ? new LinkedHashMap<>() : objectMapper.readValue(variablesJson, LinkedHashMap.class);
            Map<String, Object> nodos = vars.get("nodos") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : new LinkedHashMap<>();
            Map<String, Object> nodo = nodos.get(nodoId) instanceof Map<?, ?> m2
                    ? (Map<String, Object>) m2 : new LinkedHashMap<>();
            nodo.put("decision", puerto);
            nodo.put("comentario", comentario);
            nodo.put("decididoPor", quien.getCorporateEmailUser());
            nodos.put(nodoId, nodo);
            vars.put("nodos", nodos);
            return objectMapper.writeValueAsString(vars);
        } catch (Exception e) {
            log.warn("No se pudo anotar la decisión en las variables: {}", e.getMessage());
            return variablesJson;
        }
    }
}
