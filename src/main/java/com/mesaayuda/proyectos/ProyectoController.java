package com.mesaayuda.proyectos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Proyectos (Fase 3). Abierto a cualquier sesión a nivel de URL; quién puede
 * qué lo decide {@link ProyectoService} (gestor, jefe participante, miembro,
 * auditor en solo lectura).
 */
@RestController
@RequestMapping("/proyectos")
@Tag(name = "Proyectos", description = "Proyectos por área con tablero, sprints, objetivos y logros")
public class ProyectoController {

    private final ProyectoService servicio;

    public ProyectoController(ProyectoService servicio) {
        this.servicio = servicio;
    }

    public record AreaRequest(UUID areaId) {
    }

    @GetMapping
    @Operation(summary = "Proyectos que puede ver la persona")
    public List<ProyectoService.Resumen> listar() {
        return servicio.listar();
    }

    @PostMapping
    @Operation(summary = "Crear un proyecto (jefe: de su área; súper admin: elige el área)")
    public ResponseEntity<ProyectoService.Resumen> crear(@RequestBody ProyectoService.ProyectoRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(r));
    }

    @GetMapping("/invitaciones")
    @Operation(summary = "Invitaciones pendientes para el área del jefe")
    public List<ProyectoService.Invitacion> invitaciones() {
        return servicio.invitaciones();
    }

    @GetMapping("/{id}")
    public ProyectoService.Detalle detalle(@PathVariable UUID id) {
        return servicio.detalle(id);
    }

    @PutMapping("/{id}")
    public ProyectoService.Resumen editar(@PathVariable UUID id, @RequestBody ProyectoService.ProyectoRequest r) {
        return servicio.editar(id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- áreas

    @PostMapping("/{id}/areas")
    @Operation(summary = "Invitar a otra área (su jefe debe aceptar)")
    public ResponseEntity<Void> invitarArea(@PathVariable UUID id, @RequestBody AreaRequest r) {
        servicio.invitarArea(id, r == null ? null : r.areaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/areas/{areaId}/decidir")
    @Operation(summary = "El jefe del área invitada acepta o rechaza")
    public ResponseEntity<Void> decidir(@PathVariable UUID id, @PathVariable UUID areaId,
                                        @RequestBody ProyectoService.DecisionRequest r) {
        servicio.decidir(id, areaId, r);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/areas/{areaId}")
    public ResponseEntity<Void> quitarArea(@PathVariable UUID id, @PathVariable UUID areaId) {
        servicio.quitarArea(id, areaId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- equipo

    @GetMapping("/{id}/candidatos")
    public List<ProyectoService.MiembroVista> candidatos(@PathVariable UUID id) {
        return servicio.candidatos(id);
    }

    @PostMapping("/{id}/miembros")
    public ResponseEntity<Void> agregarMiembro(@PathVariable UUID id, @RequestBody ProyectoService.MiembroRequest r) {
        servicio.agregarMiembro(id, r);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/miembros/{userId}")
    public ResponseEntity<Void> quitarMiembro(@PathVariable UUID id, @PathVariable UUID userId) {
        servicio.quitarMiembro(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- sprints

    @PostMapping("/{id}/sprints")
    public ProyectoService.SprintVista crearSprint(@PathVariable UUID id, @RequestBody ProyectoService.SprintRequest r) {
        return servicio.crearSprint(id, r);
    }

    @PutMapping("/{id}/sprints/{sprintId}")
    public ResponseEntity<Void> editarSprint(@PathVariable UUID id, @PathVariable UUID sprintId,
                                             @RequestBody ProyectoService.SprintRequest r) {
        servicio.editarSprint(id, sprintId, r);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sprints/{sprintId}")
    public ResponseEntity<Void> eliminarSprint(@PathVariable UUID id, @PathVariable UUID sprintId) {
        servicio.eliminarSprint(id, sprintId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- tablero

    @PostMapping("/{id}/items")
    public ProyectoService.ItemVista crearItem(@PathVariable UUID id, @RequestBody ProyectoService.ItemRequest r) {
        return servicio.crearItem(id, r);
    }

    @PutMapping("/{id}/items/{itemId}")
    public ProyectoService.ItemVista editarItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                                @RequestBody ProyectoService.ItemRequest r) {
        return servicio.editarItem(id, itemId, r);
    }

    @PatchMapping("/{id}/items/{itemId}/mover")
    public ProyectoService.ItemVista moverItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                               @RequestBody ProyectoService.MoverRequest r) {
        return servicio.moverItem(id, itemId, r);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> eliminarItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        servicio.eliminarItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- objetivos

    @PostMapping("/{id}/objetivos")
    public ProyectoService.ObjetivoVista crearObjetivo(@PathVariable UUID id,
                                                       @RequestBody ProyectoService.ObjetivoRequest r) {
        return servicio.crearObjetivo(id, r);
    }

    @PutMapping("/{id}/objetivos/{objetivoId}")
    public ResponseEntity<Void> editarObjetivo(@PathVariable UUID id, @PathVariable UUID objetivoId,
                                               @RequestBody ProyectoService.ObjetivoRequest r) {
        servicio.editarObjetivo(id, objetivoId, r);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/objetivos/{objetivoId}/logro")
    @Operation(summary = "Marcar un objetivo como logrado (o devolverlo a pendiente)")
    public ResponseEntity<Void> marcarLogro(@PathVariable UUID id, @PathVariable UUID objetivoId,
                                            @RequestBody ProyectoService.LogroRequest r) {
        servicio.marcarLogro(id, objetivoId, r);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/objetivos/{objetivoId}")
    public ResponseEntity<Void> eliminarObjetivo(@PathVariable UUID id, @PathVariable UUID objetivoId) {
        servicio.eliminarObjetivo(id, objetivoId);
        return ResponseEntity.noContent().build();
    }
}
