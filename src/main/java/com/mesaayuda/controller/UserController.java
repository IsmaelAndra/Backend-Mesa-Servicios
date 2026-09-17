package com.mesaayuda.controller;

import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.dto.UserResponse;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuarios", description = "Gestión de usuarios")
public class UserController {

    @Autowired
    private UserService userService;

    private boolean isAdminOrSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private void checkAdminOrSuperAdmin() {
        if (!isAdminOrSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere rol ADMIN o SUPER_ADMIN");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil del usuario autenticado")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.findById(userService.getCurrentUser().getIdUser()));
    }

    @GetMapping("/agents")
    @Operation(summary = "Listar agentes activos")
    public ResponseEntity<List<UserResponse>> agents() {
        return ResponseEntity.ok(userService.findAll("AGENT", "ACTIVE", null));
    }

    @GetMapping
    @Operation(summary = "Listar usuarios (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<List<UserResponse>> findAll(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(userService.findAll(role, status, search));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por id")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Crear usuario (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody RegisterRequest request) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody RegisterRequest request) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del usuario (ACTIVE/INACTIVE)")
    public ResponseEntity<UserResponse> changeStatus(@PathVariable Long id,
                                                     @RequestParam String status) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(userService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (soft delete) usuario")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        checkAdminOrSuperAdmin();
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}