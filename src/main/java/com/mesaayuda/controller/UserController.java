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

    @Autowired
    private com.mesaayuda.service.JefaturaService jefaturaService;

    @Autowired
    private com.mesaayuda.activos.PermisosActivos permisosActivos;

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
                                || a.getAuthority().equals("ROLE_AREA_MANAGER")
                                || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                                || a.getAuthority().equals("ROLE_AUDITOR"));
        if (!esPersonal) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Se requiere ser parte del personal o auditor");
        }
    }

    /**
     * Permisos que no se deducen solo del rol. Hoy, si un jefe de área puede
     * gestionar usuarios (su área lo tiene marcado: Tecnología y Desarrollo). El
     * menú lo usa para mostrar "Usuarios" solo a quien de verdad puede entrar.
     */
    @GetMapping("/me/permisos")
    @Operation(summary = "Permisos de la cuenta actual que no salen del rol")
    public ResponseEntity<java.util.Map<String, Object>> misPermisos() {
        com.mesaayuda.entity.User actual = userService.getCurrentUser();
        return ResponseEntity.ok(java.util.Map.of(
                "gestionaUsuarios", UserService.puedeGestionarUsuarios(actual),
                // El jefe filtra con ellas las áreas que ofrece al configurar (SLA, equipos).
                "areasACargo", jefaturaService.areasACargo(actual),
                // Tecnología y Desarrollo (y el súper admin) gestionan los activos.
                "gestionaActivos", permisosActivos.gestiona(actual)));
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
    @Operation(summary = "Listar usuarios (SUPER_ADMIN, jefes que gestionan usuarios, AUDITOR)")
    public ResponseEntity<List<UserResponse>> findAll(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        userService.exigirLecturaDeUsuarios();
        return ResponseEntity.ok(userService.findAll(role, status, search));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por id")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        userService.exigirLecturaDeUsuarios();
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Crear usuario (SUPER_ADMIN o jefe que gestiona usuarios)")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody RegisterRequest request) {
        userService.exigirGestionDeUsuarios();
        UserService.exigirDatosDelAlta(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody RegisterRequest request,
                                               @RequestHeader(value = "X-Codigo-Mfa", required = false) String codigoMfa) {
        userService.exigirGestionDeUsuarios();
        // Editar la cuenta de otra persona (rol, correo, clave) pide el código
        // del autenticador, como el propio perfil, si quien edita tiene MFA.
        mfaService.exigirCodigoSiCorresponde(userService.getCurrentUser(), codigoMfa);
        // Al editar se exige lo mismo que al crear: si no, se podían dejar vacíos.
        UserService.exigirDatosDelAlta(request);
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del usuario (ACTIVE/INACTIVE)")
    public ResponseEntity<UserResponse> changeStatus(@PathVariable UUID id,
                                                     @RequestParam String status) {
        userService.exigirGestionDeUsuarios();
        return ResponseEntity.ok(userService.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (soft delete) usuario")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.exigirGestionDeUsuarios();
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}