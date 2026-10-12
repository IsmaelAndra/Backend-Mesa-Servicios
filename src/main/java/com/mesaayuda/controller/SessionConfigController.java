package com.mesaayuda.controller;

import com.mesaayuda.entity.SessionConfig;
import com.mesaayuda.service.SessionConfigService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Duracion de la sesion. Solo administradores: la vida del token de acceso es
 * una decision de seguridad de la institucion, no una preferencia personal.
 */
@RestController
@RequestMapping("/admin/session-config")
/**
 * Duración y caducidad de las sesiones.
 *
 * <p>Antes el backend aceptaba también a ADMIN, aunque el frontend solo enseñaba
 * esta pantalla al super administrador. Esa diferencia era el problema: un
 * ADMIN podía saltarse la interfaz y alargar la duración de todas las sesiones a
 * 30 días por su cuenta. Ajustar cuánto vive una sesión es tocar la seguridad de
 * toda la organización, no una preferencia de un administrador.
 */
@Tag(name = "Administración", description = "Configuración de la sesión")
 @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
 public class SessionConfigController {

    @Autowired
    private SessionConfigService sessionConfigService;

    @Autowired
    private UserService userService;

    @GetMapping
    @Operation(summary = "Leer la configuración de sesión")
    public ResponseEntity<?> leer() {
        SessionConfig c = sessionConfigService.config();
        return ResponseEntity.ok(Map.of(
                "sessionMinutes", c.getSessionMinutes(),
                "warningMinutes", c.getWarningMinutes(),
                "autoRenew", c.getAutoRenew(),
                "idleEnabled", c.getIdleEnabled(),
                "idleMinutes", c.getIdleMinutes(),
                "updatedBy", c.getUpdatedBy() == null ? "" : c.getUpdatedBy(),
                "updatedAt", c.getUpdatedAt() == null ? "" : c.getUpdatedAt().toString()));
    }

    @PutMapping
    @Operation(summary = "Cambiar cuánto dura la sesión antes de cerrar el token")
    public ResponseEntity<?> actualizar(@RequestBody SessionConfigRequest request) {
        UUID usuario = null;
        try {
            usuario = userService.getCurrentUser().getIdUser();
        } catch (Exception ignored) {
            // Si no hay usuario en contexto se deja updated_by vacio.
        }
        SessionConfig guardado = sessionConfigService.actualizar(
                request.sessionMinutes(), request.warningMinutes(), request.autoRenew(),
                request.idleEnabled(), request.idleMinutes(), usuario);
        return ResponseEntity.ok(Map.of(
                "sessionMinutes", guardado.getSessionMinutes(),
                "warningMinutes", guardado.getWarningMinutes(),
                "autoRenew", guardado.getAutoRenew(),
                "idleEnabled", guardado.getIdleEnabled(),
                "idleMinutes", guardado.getIdleMinutes(),
                "message", "La duración de la sesión se actualizó"));
    }

    public record SessionConfigRequest(
            Integer sessionMinutes,
            Integer warningMinutes,
            Boolean autoRenew,
            Boolean idleEnabled,
            Integer idleMinutes) {
    }
}
