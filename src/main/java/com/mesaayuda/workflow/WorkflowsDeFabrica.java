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
import java.util.Set;
import java.util.stream.Collectors;

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

    static final String CLASIFICACION_IA = "Clasificación de tickets con IA (MAPI)";
    /** Nombre anterior (la asistente se llamaba NEXA): se renombra al arrancar. */
    static final String CLASIFICACION_IA_ANTERIOR = "Clasificación de tickets con IA (NEXA)";

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
            List<User> superAdmins = userRepository
                    .findByRolUserNameRoleAndStatusUserAndDeletedUserFalseOrderByCreatedAtUserAsc("SUPER_ADMIN", "ACTIVE");
            if (superAdmins.isEmpty()) {
                return; // Se creará en un arranque posterior, cuando exista.
            }
            if (workflowRepository.existsByNameWorkflow(CLASIFICACION_IA_ANTERIOR)) {
                comoSuperAdmin(superAdmins.get(0), this::renombrarAMapi);
            }
            if (workflowRepository.existsByNameWorkflow(CLASIFICACION_IA)) {
                comoSuperAdmin(superAdmins.get(0), this::actualizarClasificacionIa);
                return;
            }
            comoSuperAdmin(superAdmins.get(0), this::crearClasificacionIa);
        } catch (RuntimeException e) {
            // Nunca impide arrancar: es una ayuda, no algo imprescindible.
            log.warn("No se pudo crear la automatización de fábrica '{}': {}", CLASIFICACION_IA, e.getMessage());
        }
    }

    private void crearClasificacionIa() {
        Workflow w = workflowService.crear(CLASIFICACION_IA,
                "Al crear un ticket, MAPI (la IA configurada, p. ej. GroqCloud) propone prioridad, categoría y tipo. "
                        + "Si está segura (confianza ≥ 0,6) las aplica, y siempre deja una nota interna con su "
                        + "resumen. Respeta el límite diario de consultas de Administración → IA.",
                WorkflowTriggerType.TICKET_CREATED.name(), null);
        workflowService.publicarVersion(w.getIdWorkflow(), grafoClasificacion());
        log.info("Creada la automatización de fábrica '{}' (en borrador, pendiente de activar)", CLASIFICACION_IA);
    }

    /**
     * Nodos de la primera versión (sin el tipo). Si la automatización sigue
     * exactamente así, nadie la modificó y se le añade el paso del tipo; si
     * alguien la cambió, no se toca.
     */
    static final Set<String> NODOS_VERSION_1 = Set.of(
            "inicio:INICIO", "nexa:NEXA", "segura:IF", "hayPrioridad:IF", "prioridad:TICKET_PRIORITY",
            "hayCategoria:IF", "categoria:TICKET_CATEGORY", "nota:TICKET_COMMENT", "fin:FIN");

    private void actualizarClasificacionIa() {
        workflowRepository.findByNameWorkflowAndDeletedWorkflowFalse(CLASIFICACION_IA).ifPresent(w ->
                workflowService.versionVigente(w.getIdWorkflow()).ifPresent(version -> {
                    if (!esVersion1(version.getDefinitionWorkflow())) {
                        return;
                    }
                    workflowService.publicarVersion(w.getIdWorkflow(), grafoClasificacion());
                    log.info("Automatización de fábrica '{}' actualizada: ahora también aplica el tipo de ticket",
                            CLASIFICACION_IA);
                }));
    }

    /**
     * La asistente pasó a llamarse MAPI. Se renombra la automatización y, si su
     * versión vigente aún dice "NEXA" en nombres de nodos o mensajes, se publica
     * una versión nueva con el texto cambiado. Solo se cambia ese texto: tipos de
     * nodo, ids, variables ({{nodos.nexa.*}}) y la configuración que haya
     * ajustado el administrador se quedan como estaban.
     */
    private void renombrarAMapi() {
        workflowRepository.findByNameWorkflowAndDeletedWorkflowFalse(CLASIFICACION_IA_ANTERIOR).ifPresent(w -> {
            w.setNameWorkflow(CLASIFICACION_IA);
            if (w.getDescriptionWorkflow() != null) {
                w.setDescriptionWorkflow(cambiarNombre(w.getDescriptionWorkflow()));
            }
            workflowRepository.save(w);
            workflowService.versionVigente(w.getIdWorkflow()).ifPresent(version -> {
                WorkflowGraphDef nuevo = conTextosMapi(version.getDefinitionWorkflow());
                if (nuevo != null) {
                    workflowService.publicarVersion(w.getIdWorkflow(), nuevo);
                }
            });
            log.info("Automatización de fábrica renombrada a '{}'", CLASIFICACION_IA);
        });
    }

    /** "NEXA" como palabra (no dentro de {{nodos.nexa.x}}, que va en minúsculas). */
    static String cambiarNombre(String texto) {
        return texto == null ? null : texto.replaceAll("\\bNEXA\\b", "MAPI");
    }

    /**
     * El grafo con "NEXA" cambiado a "MAPI" en el nombre de cada nodo y en sus
     * textos de configuración; null si no había nada que cambiar.
     */
    static WorkflowGraphDef conTextosMapi(String definicion) {
        try {
            WorkflowGraphDef g = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(definicion, WorkflowGraphDef.class);
            if (g.nodes() == null) {
                return null;
            }
            boolean cambio = false;
            List<WorkflowNodeDef> nodos = new java.util.ArrayList<>();
            for (WorkflowNodeDef n : g.nodes()) {
                String nombre = cambiarNombre(n.name());
                Map<String, Object> config = new LinkedHashMap<>();
                if (n.config() != null) {
                    for (Map.Entry<String, Object> e : n.config().entrySet()) {
                        Object v = e.getValue();
                        if (v instanceof String texto) {
                            String nuevo = cambiarNombre(texto);
                            cambio |= !nuevo.equals(texto);
                            v = nuevo;
                        }
                        config.put(e.getKey(), v);
                    }
                }
                cambio |= nombre != null && !nombre.equals(n.name());
                nodos.add(new WorkflowNodeDef(n.id(), n.type(), nombre, config, n.positionX(), n.positionY()));
            }
            if (!cambio) {
                return null;
            }
            return new WorkflowGraphDef(CLASIFICACION_IA, nodos, g.edges(), g.variables());
        } catch (Exception e) {
            return null;
        }
    }

    /** Si la definición guardada es la de la primera versión de fábrica, sin cambios. */
    static boolean esVersion1(String definicion) {
        try {
            WorkflowGraphDef g = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(definicion, WorkflowGraphDef.class);
            Set<String> nodos = g.nodes() == null ? Set.of()
                    : g.nodes().stream().map(n -> n.id() + ":" + n.type()).collect(Collectors.toSet());
            return NODOS_VERSION_1.equals(nodos);
        } catch (Exception e) {
            return false;
        }
    }

    /** INICIO → NEXA → ¿segura? → prioridad → categoría → tipo → nota interna → FIN. */
    static WorkflowGraphDef grafoClasificacion() {
        List<WorkflowNodeDef> nodos = List.of(
                nodo("inicio", "INICIO", "Ticket creado", Map.of(), 80, 200),
                nodo("nexa", "NEXA", "MAPI clasifica", Map.of(
                        "accion", "CLASIFICAR",
                        "instrucciones", "Clasifica el ticket con las reglas de la institución. "
                                + "Si dudas, deja la prioridad en MEDIA."), 280, 200),
                nodo("segura", "IF", "¿MAPI está segura?", Map.of(
                        "campo", "nodos.nexa.confianza", "operador", ">=", "valor", 0.6), 480, 200),
                nodo("hayPrioridad", "IF", "¿Propuso prioridad?", Map.of(
                        "campo", "nodos.nexa.prioridadId", "operador", "no_vacio"), 680, 120),
                nodo("prioridad", "TICKET_PRIORITY", "Aplicar prioridad", Map.of(
                        "prioridadId", "{{nodos.nexa.prioridadId}}"), 880, 60),
                nodo("hayCategoria", "IF", "¿Propuso categoría?", Map.of(
                        "campo", "nodos.nexa.categoriaId", "operador", "no_vacio"), 1080, 120),
                nodo("categoria", "TICKET_CATEGORY", "Aplicar categoría", Map.of(
                        "categoriaId", "{{nodos.nexa.categoriaId}}"), 1280, 60),
                nodo("hayTipo", "IF", "¿Propuso tipo?", Map.of(
                        "campo", "nodos.nexa.tipoId", "operador", "no_vacio"), 1480, 120),
                nodo("tipo", "TICKET_TYPE", "Aplicar tipo", Map.of(
                        "tipoId", "{{nodos.nexa.tipoId}}"), 1680, 60),
                nodo("nota", "TICKET_COMMENT", "Nota interna de MAPI", Map.of(
                        "mensaje", "MAPI: {{nodos.nexa.resumen}} (prioridad sugerida: {{nodos.nexa.prioridad}}, "
                                + "categoría: {{nodos.nexa.categoria}}, tipo: {{nodos.nexa.tipo}}, "
                                + "confianza: {{nodos.nexa.confianza}})",
                        "interno", true), 1880, 200),
                nodo("fin", "FIN", "Fin", Map.of(), 2080, 200));
        List<WorkflowEdgeDef> aristas = List.of(
                arista("inicio", "nexa", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("nexa", "segura", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("segura", "hayPrioridad", "SI", "Sí"),
                arista("segura", "nota", "NO", "No"),
                arista("hayPrioridad", "prioridad", "SI", "Sí"),
                arista("hayPrioridad", "hayCategoria", "NO", "No"),
                arista("prioridad", "hayCategoria", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("hayCategoria", "categoria", "SI", "Sí"),
                arista("hayCategoria", "hayTipo", "NO", "No"),
                arista("categoria", "hayTipo", WorkflowEdgeDef.PUERTO_UNICO, null),
                arista("hayTipo", "tipo", "SI", "Sí"),
                arista("hayTipo", "nota", "NO", "No"),
                arista("tipo", "nota", WorkflowEdgeDef.PUERTO_UNICO, null),
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
