package com.mesaayuda.controller;

import com.mesaayuda.dto.AgentOptionResponse;
import com.mesaayuda.dto.ProfileRequest;
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
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuarios", description = "Gestión de usuarios")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.mesaayuda.service.MfaService mfaService;

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

    /**
     * Exige ser parte del personal: agente, administrador o super administrador.
     *
     * <p>Se usa en el listado de agentes porque lo consume el kanban, que es una
     * pantalla de trabajo del personal. Un ADMIN no basta como requisito, o el
     * propio rol AGENT se quedaria fuera de la aplicacion.
     */
    private void checkPersonal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esPersonal = auth != null && auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_AGENT")
                                || a.getAuthority().equals("ROLE_ADMIN")
                                || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!esPersonal) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Se requiere rol AGENT, ADMIN o SUPER_ADMIN");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil del usuario autenticado")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.findById(userService.getCurrentUser().getIdUser()));
    }

    /**
     * Guarda el perfil propio. Va antes que /{id} a proposito: si se declarara
     * despues, Spring lo capturaria "me" como si fuera un identificador.
     *
     * No exige rol de administracion: cada persona edita su propia ficha. Antes
     * el formulario llamaba a PUT /users/{id}, que es la ruta de administracion
     * y ademas validaba un RegisterRequest con roleName y areaId, que el
     * formulario no envia, y por eso respondia 400.
     */
    @PutMapping("/me")
    @Operation(summary = "Actualizar mi perfil")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody ProfileRequest request) {
        // Con doble factor, cambiar los datos personales pide también el código.
        mfaService.exigirCodigoSiCorresponde(userService.getCurrentUser(), request.codigoMfa());
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }

@GetMapping("/agents")
  @Operation(summary = "Listar agentes activos (para asignar tickets)")
  public ResponseEntity<List<AgentOptionResponse>> agents() {
      // Restringido al personal: el listado lo usa el kanban para asignar, y ningun
      // solicitante necesita elegir agente al reportar. Antes no habia ninguna
      // comprobacion y se servia el UserResponse completo, con lo que cualquier
      // usuario con sesion obtenia la identificacion, el telefono, la foto y el
      // UPN de Microsoft de todo el personal.
      checkPersonal();
      return ResponseEntity.ok(userService.findAgentOptions());
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
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
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
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody RegisterRequest request) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del usuario (ACTIVE/INACTIVE)")
    public ResponseEntity<UserResponse> changeStatus(@PathVariable UUID id,
                                                     @RequestParam String status) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(userService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (soft delete) usuario")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}