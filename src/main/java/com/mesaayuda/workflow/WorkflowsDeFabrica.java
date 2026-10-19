package com.mesaayuda.workflow;

import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.security.MesaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatizaciones que trae la mesa de fábrica.
 *
 * <p>Se crean una sola vez, al arrancar, con el mismo servicio que usa el
 * constructor (mismas validaciones) y en nombre del primer SUPER_ADMIN, que es
 * quien puede programar workflows. Quedan en BORRADOR: alguien tiene que
 * revisarlas y activarlas. Si se borran no vuelven a aparecer (se comprueba el
 * nombre también entre las borradas).
 */
@Component
public class WorkflowsDeFabrica {

    private static final Logger log = LoggerFactory.getLogger(WorkflowsDeFabrica.class);

    static final String CLASIFICACION_IA = "Clasificación de tickets con IA (NEXA)";

    private final WorkflowRepository workflowRepository;
    private final WorkflowService workflowService;
    private final UserRepository userRepository;

    public WorkflowsDeFabrica(WorkflowRepository workflowRepository, WorkflowService workflowService,
                              UserRepository userRepository) {
        this.workflowRepository = workflowRepository;
        this.workflowService = workflowService;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crear() {
        try {
            if (workflowRepository.existsByNameWorkflow(CLASIFICACION_IA)) {
                return;
            }
            List<User> superAdmins = userRepository
                    .findByRolUserNameRoleAndStatusUserAndDeletedUserFalseOrderByCreatedAtUserAsc("SUPER_ADMIN", "ACTIVE");
            if (superAdmins.isEmpty()) {
                return; // Se creará en un arranque posterior, cuando exista.
            }
            comoSuperAdmin(superAdmins.get(0), this::crearClasificacionIa);
        } catch (RuntimeException e) {
            // Nunca impide arrancar: es una ayuda, no algo imprescindible.
            log.warn("No se pudo crear la automatización de fábrica '{}': {}", CLASIFICACION_IA, e.getMessage());
        }
    }

    private void crearClasificacionIa() {
        Workflow w = workflowService.crear(CLASIFICACION_IA,
                "Al crear un ticket, NEXA (la IA configurada, p. ej. GroqCloud) propone prioridad y categoría. "
                        + "Si está segura (confianza ≥ 0,6) las aplica, y siempre deja una nota interna con su "
                        + "resumen. Respeta el límite diario de consultas de Administración → IA.",
                WorkflowTriggerType.TICKET_CREATED.name(), null);
        workflowService.publicarVersion(w.getIdWorkflow(), grafoClasificacion());
        log.info("Creada la automatización de fábrica '{}' (en borrador, pendiente de activar)", CLASIFICACION_IA);
    }

    /** INICIO → NEXA → ¿segura? → prioridad → categoría → nota interna → FIN. */
    static WorkflowGraphDef grafoClasificacion() {
        List<WorkflowNodeDef> nodos = List.of(
                nodo("inicio", "INICIO", "Ticket creado", Map.of(), 80, 200),
                nodo("nexa", "NEXA", "NEXA clasifica", Map.of(
                        "accion", "CLASIFICAR",
                        "instrucciones", "Clasifica el ticket con las reglas de la institución. "
                                + "Si dudas, deja la prioridad en MEDIA."), 280, 200),
                nodo("segura", "IF", "¿NEXA está segura?", Map.of(
                        "campo", "nodos.nexa.confianza", "operador", ">=", "valor", 0.6), 480, 200),
                nodo("hayPrioridad", "IF", "¿Propuso prioridad?", Map.of(
                        "campo", "nodos.nexa.prioridadId", "operador", "no_vacio"), 680, 120),
                nodo("prioridad", "TICKET_PRIORITY", "Aplicar prioridad", Map.of(
                        "prioridadId", "{{nodos.nexa.prioridadId}}"), 880, 60),
                nodo("hayCategoria", "IF", "¿Propuso categoría?", Map.of(
                        "campo", "nodos.nexa.categoriaId", "operador", "no_vacio"), 1080, 120),
                nodo("categoria", "TICKET_CATEGORY", "Aplicar categoría", Map.of(
                        "categoriaId", "{{nodos.nexa.categoriaId}}"), 1280, 60),
                nodo("nota", "TICKET_COMMENT", "Nota interna de NEXA", Map.of(
                        "mensaje", "NEXA: {{nodos.nexa.resumen}} (prioridad sugerida: {{nodos.nexa.prioridad}}, "
                                + "categoría: {{nodos.nexa.categoria}}, confianza: {{nodos.nexa.confianza}})",
                        "interno", true), 1480, 200),
                nodo("fin", "FIN", "Fin", Map.of(), 1680, 200));
        List<WorkflowEdgeDef> aristas = List.of(
                arista("inicio", "nexa", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("nexa", "segura", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("segura", "hayPrioridad", "SI", "Sí"),
                arista("segura", "nota", "NO", "No"),
                arista("hayPrioridad", "prioridad", "SI", "Sí"),
                arista("hayPrioridad", "hayCategoria", "NO", "No"),
                arista("prioridad", "hayCategoria", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("hayCategoria", "categoria", "SI", "Sí"),
                arista("hayCategoria", "nota", "NO", "No"),
                arista("categoria", "nota", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("nota", "fin", WorkflowEdgeDef.PUERTO_UNICO, null));
        return new WorkflowGraphDef(CLASIFICACION_IA, nodos, aristas, Map.of());
    }

    private static WorkflowNodeDef nodo(String id, String tipo, String nombre, Map<String, Object> config,
                                        double x, double y) {
        return new WorkflowNodeDef(id, tipo, nombre, new LinkedHashMap<>(config), x, y);
    }

    private static WorkflowEdgeDef arista(String desde, String hasta, String puerto, String etiqueta) {
        return new WorkflowEdgeDef(desde, hasta, puerto, etiqueta);
    }

    /** Ejecuta con la sesión del SUPER_ADMIN y restaura la anterior pase lo que pase. */
    private void comoSuperAdmin(User autor, Runnable accion) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        try {
            List<SimpleGrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            MesaUserDetails detalles = new MesaUserDetails(autor.getCorporateEmailUser(),
                    "fabrica-sin-contrasena", autoridades, false);
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new UsernamePasswordAuthenticationToken(detalles, null, autoridades));
            SecurityContextHolder.setContext(contexto);
            accion.run();
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }
}
