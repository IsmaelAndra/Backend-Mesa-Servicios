package com.mesaayuda.controller;

import java.util.UUID;
import com.mesaayuda.dto.WorkflowAprobacionResponse;
import com.mesaayuda.workflow.WorkflowAprobacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Bandeja de aprobaciones de workflow.
 *
 * <p>Solo para ADMIN y SUPER_ADMIN. El ADMIN origina las solicitudes y ve cómo
 * se resolvieron ({@code /mias}); solo el SUPER_ADMIN decide.
 */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
@RequestMapping("/workflows/aprobaciones")
@Tag(name = "Aprobaciones de workflow", description = "Bandeja y decisión de los pasos de aprobación")
public class WorkflowAprobacionController {

    private final WorkflowAprobacionService service;

    public WorkflowAprobacionController(WorkflowAprobacionService service) {
        this.service = service;
    }

    @Operation(summary = "Aprobaciones pendientes que puedo decidir")
    @GetMapping
    public ResponseEntity<List<WorkflowAprobacionResponse>> pendientes() {
        return ResponseEntity.ok(service.pendientes());
    }

    @Operation(summary = "Mis solicitudes: las aprobaciones que provoqué y cómo se resolvieron")
    @GetMapping("/mias")
    public ResponseEntity<List<WorkflowAprobacionResponse>> mias() {
        return ResponseEntity.ok(service.misSolicitudes());
    }

    @Operation(summary = "Pendientes del menú: por decidir (SUPER_ADMIN) o mis solicitudes en espera")
    @GetMapping("/contador")
    public ResponseEntity<Map<String, Long>> contador() {
        return ResponseEntity.ok(Map.of("pendientes", service.contarPendientes()));
    }

    /** Cuerpo: {@code {"aprobar": true|false, "comentario": "..."}}. El comentario es obligatorio al rechazar. */
    @Operation(summary = "Aprobar o rechazar")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PostMapping("/{id}/decidir")
    public ResponseEntity<Void> decidir(@PathVariable UUID id, @RequestBody Map<String, Object> cuerpo) {
        boolean aprobar = Boolean.TRUE.equals(cuerpo == null ? null : cuerpo.get("aprobar"));
        Object comentario = cuerpo == null ? null : cuerpo.get("comentario");
        service.decidir(id, aprobar, comentario == null ? null : String.valueOf(comentario));
        return ResponseEntity.noContent().build();
    }
}
