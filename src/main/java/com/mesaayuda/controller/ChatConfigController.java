package com.mesaayuda.controller;

import com.mesaayuda.entity.ChatConfig;
import com.mesaayuda.service.ChatVisibilityPolicy;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Visibilidad de los acuses de lectura del chat. Solo administradores: quien ve
 * los "Leido por ..." se decide por institucion, no por usuario.
 * <p>
 * El GET existe ademas para el propio chat, porque el frontend necesita saber si
 * puede pintar el doble check. Por eso devuelve tambien {@code canSeeRead} para el
 * usuario que pregunta, y por eso el resto de campos solo se expone a
 * administradores.
 */
@RestController
@RequestMapping("/admin/chat-config")
@Tag(name = "Administración", description = "Configuración del chat")
// Solo el super administrador, y no por ser una preferencia cualquiera: quien
// decide quién ve los acuses de lectura decide quién puede saber que otro usuario
// ha leído un mensaje. Con ADMIN autorizado, un administrador podría activarlo y
// leer la conversación de un ticket en el que no participa.
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class ChatConfigController {

    @Autowired
    private ChatVisibilityPolicy chatVisibilityPolicy;

    @Autowired
    private UserService userService;

    /**
     * Ajuste actual. Lo consulta el admin para pintar el selector. El endpoint
     * esta restringido a administradores por el {@code @PreAuthorize} de la clase.
     */
    @GetMapping
    @Operation(summary = "Leer quién puede ver los acuses de lectura del chat")
    public ResponseEntity<?> leer() {
        ChatConfig c = chatVisibilityPolicy.config();
        return ResponseEntity.ok(Map.of(
                "readVisibleTo", c.getReadVisibleTo(),
                "showReadByNames", c.getShowReadByNames(),
                "options", ChatVisibilityPolicy.OPCIONES.stream()
                        .map(o -> Map.of("valor", o.valor(), "texto", o.texto()))
                        .toList(),
                "updatedBy", c.getUpdatedBy() == null ? "" : c.getUpdatedBy()));
    }

    /**
     * Lo que necesita el chat para pintar el doble check. Es de solo lectura y
     * lo puede pedir cualquier usuario con sesion: no expone nada del ajuste, solo
     * si esta permitido mostraselo. El token de sesion va en la cabecera y el
     * filtro de seguridad lo comprueba como el resto de endpoints.
     */
    @GetMapping("/mi-visibilidad")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Si el usuario actual puede ver los acuses de lectura")
    public ResponseEntity<?> miVisibilidad() {
        return ResponseEntity.ok(Map.of(
                "canSeeRead", chatVisibilityPolicy.puedeVer(userService.getCurrentUser()),
                "showReadByNames", chatVisibilityPolicy.muestraNombres()));
    }

    @PutMapping
    @Operation(summary = "Cambiar quién puede ver los acuses de lectura del chat")
    public ResponseEntity<?> actualizar(@RequestBody ChatConfigRequest request) {
        UUID usuario = null;
        try {
            usuario = userService.getCurrentUser().getIdUser();
        } catch (Exception ignored) {
            // Sin usuario en contexto se deja updated_by vacio.
        }
        try {
            ChatConfig guardado = chatVisibilityPolicy.actualizar(
                    request.readVisibleTo(), request.showReadByNames(), usuario);
            return ResponseEntity.ok(Map.of(
                    "readVisibleTo", guardado.getReadVisibleTo(),
                    "showReadByNames", guardado.getShowReadByNames(),
                    "message", "La visibilidad de los acuses de lectura se actualizó"));
        } catch (IllegalArgumentException ex) {
            // No se lanza excepcion global: es un dato de formulario invalido y la
            // pantalla lo enseia junto al selector.
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    public record ChatConfigRequest(String readVisibleTo, Boolean showReadByNames) {
    }
}
