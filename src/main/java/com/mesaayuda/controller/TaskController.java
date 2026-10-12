package com.mesaayuda.controller;

import com.mesaayuda.dto.StatusChangeRequest;
import com.mesaayuda.dto.TaskRequest;
import com.mesaayuda.dto.TaskResponse;
import com.mesaayuda.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
@Tag(name = "Tareas", description = "Actividades no-ticket para kanban y calendario")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping
    @Operation(summary = "Listar tareas (AGENT ve las suyas; ADMIN/SUPER_ADMIN todas)")
    public ResponseEntity<List<TaskResponse>> findAll(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(taskService.findAll(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener tarea por id")
    public ResponseEntity<TaskResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Crear tarea (envía acuerdo por correo al asignado)")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar tarea")
    public ResponseEntity<TaskResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado de la tarea (PENDIENTE/EN_CURSO/EN_REVISION/HECHO)")
    public ResponseEntity<TaskResponse> changeStatus(@PathVariable UUID id,
                                                     @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(taskService.changeStatus(id, request.statusName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (soft delete) tarea")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}