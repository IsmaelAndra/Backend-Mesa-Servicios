package com.mesaayuda.controller;

import com.mesaayuda.dto.GraphValidationResponse;
import com.mesaayuda.dto.WorkflowCreateRequest;
import com.mesaayuda.dto.WorkflowDetailResponse;
import com.mesaayuda.dto.WorkflowExecutionResponse;
import com.mesaayuda.dto.WorkflowNodeRunResponse;
import com.mesaayuda.dto.WorkflowSummaryResponse;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.workflow.WorkflowExecutionService;
import com.mesaayuda.workflow.WorkflowGraphDef;
import com.mesaayuda.workflow.WorkflowService;
import com.mesaayuda.workflow.WorkflowTriggerService;
import com.mesaayuda.workflow.WorkflowValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestión de workflows.
 *
 * <p>Todo el controlador exige {@code ROLE_SUPER_ADMIN}, no los dos roles de
 * administración. Programar una automatización es escribir código con permisos
 * de alguien: si un ADMIN pudiera hacerlo, podría dejar procesos moviendo tickets
 * y notificando a usuarios por debajo de la mesa. Es un cambio de comportamiento,
 * no una configuración, y por eso queda fuera del alcance del ADMIN.
 *
 * <p>La vista de solo lectura tampoco se abre: ver los procesos en marcha es
 * información operativa, y no aporta nada al ADMIN que no vea en el dashboard.
 */
@RestController
@RequestMapping("/admin/workflows")
@Tag(name = "Workflows", description = "Definición y versionado de automatizaciones (SUPER_ADMIN)")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class WorkflowController {

    private final WorkflowService service;
    private final WorkflowExecutionService executionService;
    private final WorkflowTriggerService triggerService;
    private final WorkflowValidationService validation;
    private final com.mesaayuda.service.UserService userService;
    private final com.mesaayuda.service.SystemAuditService systemAuditService;
    private final com.mesaayuda.repository.TicketRepository ticketRepository;
    private final com.mesaayuda.repository.UserRepository userRepository;

    public WorkflowController(WorkflowService service,
                              WorkflowExecutionService executionService,
                              WorkflowTriggerService triggerService,
                              WorkflowValidationService validation,
                              com.mesaayuda.service.UserService userService,
                              com.mesaayuda.service.SystemAuditService systemAuditService,
                              com.mesaayuda.repository.TicketRepository ticketRepository,
                              com.mesaayuda.repository.UserRepository userRepository) {
        this.service = service;
        this.executionService = executionService;
        this.triggerService = triggerService;
        this.validation = validation;
        this.userService = userService;
        this.systemAuditService = systemAuditService;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Catálogo del motor para el constructor",
            description = "Disparadores con sus variables, tipos de nodo con sus puertos y salidas, y operadores")
    @GetMapping("/catalogo")
    public ResponseEntity<Map<String, Object>> catalogo() {
        return ResponseEntity.ok(com.mesaayuda.workflow.WorkflowCatalogo.completo());
    }

    @Operation(summary = "Listar workflows")
    @GetMapping
    public ResponseEntity<List<WorkflowSummaryResponse>> listar() {
        List<WorkflowSummaryResponse> lista = service.listar().stream()
                .map(WorkflowSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Ver un workflow con sus versiones")
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDetailResponse> detalle(@PathVariable UUID id) {
        Workflow w = service.obtener(id);
        return ResponseEntity.ok(WorkflowDetailResponse.from(w, service.versiones(id)));
    }

    @Operation(summary = "Crear un workflow en borrador")
    @PostMapping
    public ResponseEntity<WorkflowSummaryResponse> crear(
            @Valid @RequestBody WorkflowCreateRequest request) {
        // Nace en BORRADOR y sin publicar. No se ejecuta hasta que alguien lo
        // publique y lo active, que son dos pasos deliberadamente separados.
        Workflow creado = service.crear(request.name(), request.description(),
                request.triggerType(), request.triggerConfig());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowSummaryResponse.from(creado));
    }

    @Operation(summary = "Cambiar nombre, descripción o disparador de un workflow")
    @PutMapping("/{id}")
    public ResponseEntity<WorkflowSummaryResponse> actualizar(
            @PathVariable UUID id, @Valid @RequestBody WorkflowCreateRequest request) {
        Workflow w = service.actualizar(id, request.name(), request.description(),
                request.triggerType(), request.triggerConfig());
        return ResponseEntity.ok(WorkflowSummaryResponse.from(w));
    }

    @Operation(summary = "Publicar una versión nueva del grafo")
    @PostMapping("/{id}/versions")
    public ResponseEntity<WorkflowDetailResponse.VersionResponse> publicar(
            @PathVariable UUID id,
            @RequestBody WorkflowGraphDef grafo) {
        // El grafo se valida en el servicio, antes de tocar la base. Publicar un
        // grafo roto produciría un workflow que falla al dispararse, no ahora.
        WorkflowVersion publicada = service.publicarVersion(id, grafo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkflowDetailResponse.VersionResponse.from(publicada));
    }

    @Operation(summary = "Activar un workflow")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activar(@PathVariable UUID id) {
        service.activar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desactivar un workflow")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar un workflow")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(@PathVariable UUID id) {
        // Borrado lógico: el historial de ejecuciones se conserva para poder
        // explicar qué hizo el proceso.
        service.borrar(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ Observabilidad ============================

    @Operation(summary = "Validar un grafo sin guardarlo")
    @PostMapping("/{id}/validate")
    public ResponseEntity<GraphValidationResponse> validar(
            @PathVariable UUID id,
            @RequestBody WorkflowGraphDef grafo) {
        // Valida contra el mismo servicio que usa el alta, así que el constructor
        // no puede dar por bueno un grafo que el servidor rechazaría. El 404 sale
        // solo: validar un workflow que no existe no tiene sentido.
        Workflow w = service.obtener(id);
        List<String> errores = validation.validar(grafo, w.getTriggerType());
        return ResponseEntity.ok(new GraphValidationResponse(errores.isEmpty(), errores));
    }

    @Operation(summary = "Historial de ejecuciones de un workflow")
    @GetMapping("/{id}/ejecuciones")
    public ResponseEntity<Page<WorkflowExecutionResponse>> ejecuciones(
            @PathVariable UUID id,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano) {
        Workflow w = service.obtener(id);
        Pageable pageable = PageRequest.of(Math.max(0, pagina),
                Math.min(200, Math.max(1, tamano)));

        Page<WorkflowExecution> paginaEjecuciones = estado == null || estado.isBlank()
                ? executionService.consultar(id, null, pageable)
                : executionService.consultar(id, estado.trim().toUpperCase(java.util.Locale.ROOT), pageable);

        return ResponseEntity.ok(paginaEjecuciones
                .map(e -> executionService.respuesta(e, w.getNameWorkflow())));
    }

    @Operation(summary = "Historial de ejecuciones de todos los workflows",
            description = "Filtros opcionales: workflowId, estado, entidadTipo (TICKET/USUARIO) y entidadId")
    @GetMapping("/ejecuciones")
    public ResponseEntity<Page<WorkflowExecutionResponse>> todasLasEjecuciones(
            @RequestParam(required = false) UUID workflowId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String entidadTipo,
            @RequestParam(required = false) UUID entidadId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano) {
        Pageable pageable = PageRequest.of(Math.max(0, pagina), Math.min(200, Math.max(1, tamano)));
        Map<UUID, String> nombres = new java.util.HashMap<>();
        return ResponseEntity.ok(executionService.buscar(workflowId, estado, entidadTipo, entidadId, pageable)
                .map(e -> executionService.respuesta(e, nombres.computeIfAbsent(e.getWorkflowWorkflow(),
                        k -> executionService.nombreWorkflow(e)))));
    }

    @Operation(summary = "Detalle de una ejecución")
    @GetMapping("/ejecuciones/{ejecucionId}")
    public ResponseEntity<WorkflowExecutionResponse> ejecucion(@PathVariable UUID ejecucionId) {
        WorkflowExecution ejecucion = executionService.obtener(ejecucionId);
        String nombre = executionService.nombreWorkflow(ejecucion);
        return ResponseEntity.ok(executionService.respuesta(ejecucion, nombre));
    }

    @Operation(summary = "Pasos recorridos por una ejecución")
    @GetMapping("/ejecuciones/{ejecucionId}/pasos")
    public ResponseEntity<List<WorkflowNodeRunResponse>> pasos(@PathVariable UUID ejecucionId) {
        executionService.obtener(ejecucionId);
        return ResponseEntity.ok(executionService.pasos(ejecucionId).stream()
                .map(WorkflowNodeRunResponse::from)
                .toList());
    }

    @Operation(summary = "Cancelar una ejecución en curso")
    @PostMapping("/ejecuciones/{ejecucionId}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable UUID ejecucionId) {
        executionService.cancelar(ejecucionId);
        // Las rutas de workflows no pasan por la auditoría automática de /admin
        // (cada operación registra la suya), así que se registra aquí.
        systemAuditService.record("WORKFLOW_EXECUTION_CANCEL", "WorkflowExecution", ejecucionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ejecutar un workflow a mano")
    @PostMapping("/{id}/ejecutar")
    public ResponseEntity<WorkflowExecutionResponse> ejecutar(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> payload) {
        // Se encola, no se ejecuta aquí: una llamada a MAPI tarda hasta un minuto y
        // colgar de la petición pararía el help desk. La respuesta es la ejecución
        // encolada, y el resultado se mira en el historial.
        Workflow w = service.obtener(id);
        com.mesaayuda.entity.User actual = userService.getCurrentUser();
        // Quien la lanza queda como iniciador, no el autor del workflow: es la
        // persona que hay que poder identificar en el historial.
        WorkflowExecution encolada = triggerService.ejecutarManualmente(w,
                conEntidadResuelta(payload), actual.getIdUser());
        systemAuditService.record("WORKFLOW_MANUAL_RUN", "Workflow", id, actual.getCorporateEmailUser(),
                "Ejecución manual " + encolada.getIdWorkflowExecution() + " del workflow '" + w.getNameWorkflow()
                        + "'" + (encolada.getEntityIdExecution() == null ? ""
                        : " sobre " + encolada.getEntityTypeExecution() + " #" + encolada.getEntityIdExecution()));
        return ResponseEntity.accepted().body(executionService.respuesta(encolada, w.getNameWorkflow()));
    }

    /**
     * En "Probar" la persona escribe lo que conoce: el número del ticket ("43" o
     * "#43") o el correo del usuario, no su uuid. Se traduce aquí a la clave
     * interna; si ya viene un uuid, se deja tal cual.
     */
    Map<String, Object> conEntidadResuelta(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Map<String, Object> copia = new java.util.HashMap<>(payload);
        Object ticket = payload.get("ticketId");
        if (ticket != null) {
            String texto = String.valueOf(ticket).trim();
            if (texto.startsWith("#")) {
                texto = texto.substring(1).trim();
            }
            if (texto.matches("\\d{1,18}")) {
                Long numero = Long.valueOf(texto);
                copia.put("ticketId", ticketRepository.findByNumberTicketAndDeletedTicketFalse(numero)
                        .map(com.mesaayuda.entity.Ticket::getIdTicket)
                        .orElseThrow(() -> new com.mesaayuda.exception.BadRequestException(
                                "No existe el ticket #" + numero)));
            }
        }
        Object usuario = payload.get("usuarioId");
        if (usuario != null && String.valueOf(usuario).contains("@")) {
            String correo = String.valueOf(usuario).trim();
            copia.put("usuarioId", userRepository.findByCorporateEmailUser(correo)
                    .map(com.mesaayuda.entity.User::getIdUser)
                    .orElseThrow(() -> new com.mesaayuda.exception.BadRequestException(
                            "No existe un usuario con el correo " + correo)));
        }
        return copia;
    }

    @Operation(summary = "Purgar ejecuciones terminadas fuera de retención")
    @PostMapping("/ejecuciones/purgar")
    public ResponseEntity<Map<String, Object>> purgar(@RequestParam Integer dias) {
        int borradas = executionService.purgar(dias);
        systemAuditService.record("WORKFLOW_PURGE", "WorkflowExecution", null,
                userService.getCurrentUser().getCorporateEmailUser(),
                "Purgadas " + borradas + " ejecuciones con más de " + dias + " días");
        return ResponseEntity.ok(Map.of("purgadas", borradas));
    }
}