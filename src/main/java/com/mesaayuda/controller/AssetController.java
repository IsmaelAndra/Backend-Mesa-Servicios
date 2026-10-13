package com.mesaayuda.controller;

import com.mesaayuda.dto.AssetAssignableUserResponse;
import com.mesaayuda.dto.AssetHistoryResponse;
import com.mesaayuda.dto.AssetRequest;
import com.mesaayuda.dto.AssetResponse;
import com.mesaayuda.dto.StatusChangeRequest;
import com.mesaayuda.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/assets")
@PreAuthorize("hasAnyRole('USER','AGENT','ADMIN','SUPER_ADMIN','AREA_MANAGER')")
@Tag(name = "Activos", description = "Gestión de activos de TI (laptops, monitores, etc.)")
public class AssetController {

    @Autowired
    private AssetService assetService;

    @GetMapping
    @Operation(summary = "Listar activos con paginación y filtros")
    public ResponseEntity<Page<AssetResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) UUID sucursalId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(assetService.findAll(search, status, type, areaId, sucursalId, pageable));
    }

    @GetMapping("/asignables")
    @Operation(summary = "Personas a las que el usuario puede asignar activos (todas o las de su área)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<List<AssetAssignableUserResponse>> asignables() {
        return ResponseEntity.ok(assetService.asignables());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener activo por id")
    public ResponseEntity<AssetResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo activo (ADMIN/SUPER_ADMIN/AREA_MANAGER)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar activo (ADMIN/SUPER_ADMIN/AREA_MANAGER)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<AssetResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody AssetRequest request) {
        return ResponseEntity.ok(assetService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del activo (DISPONIBLE/ASIGNADO/EN_REPARACION/BAJA)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<AssetResponse> changeStatus(@PathVariable UUID id,
                                                      @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(assetService.changeStatus(id, request.statusName()));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Asignar activo a un usuario (ADMIN/SUPER_ADMIN/AREA_MANAGER)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<AssetResponse> assign(@PathVariable UUID id,
                                                @RequestParam UUID userId,
                                                @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(assetService.assign(id, userId, notes));
    }

    @PostMapping("/{id}/unassign")
    @Operation(summary = "Desasignar activo (ADMIN/SUPER_ADMIN/AREA_MANAGER)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<AssetResponse> unassign(@PathVariable UUID id,
                                                  @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(assetService.unassign(id, notes));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Obtener historial de asignaciones del activo")
    public ResponseEntity<List<AssetHistoryResponse>> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getHistory(id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Obtener activos asignados a un usuario")
    public ResponseEntity<List<AssetResponse>> findByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(assetService.findByUser(userId));
    }

    @GetMapping("/area/{areaId}")
    @Operation(summary = "Obtener activos de un área (ADMIN/SUPER_ADMIN/AREA_MANAGER)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','AREA_MANAGER')")
    public ResponseEntity<List<AssetResponse>> findByArea(@PathVariable UUID areaId) {
        return ResponseEntity.ok(assetService.findByArea(areaId));
    }

    @GetMapping("/sucursal/{sucursalId}")
    @Operation(summary = "Obtener activos de una sucursal (ADMIN/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<AssetResponse>> findBySucursal(@PathVariable UUID sucursalId) {
        return ResponseEntity.ok(assetService.findBySucursal(sucursalId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (baja lógica) activo (ADMIN/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}