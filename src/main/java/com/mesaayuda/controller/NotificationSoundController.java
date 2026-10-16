package com.mesaayuda.controller;

import com.mesaayuda.dto.NotificationSoundResponse;
import com.mesaayuda.entity.NotificationSound;
import com.mesaayuda.service.NotificationSoundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Sonidos de las notificaciones. Los lee cualquier usuario con sesión (le suenan
 * a todos); solo el SUPER_ADMIN los cambia.
 */
@RestController
@Tag(name = "Sonidos", description = "Sonidos de las notificaciones para todos los usuarios")
public class NotificationSoundController {

    private final NotificationSoundService service;

    public NotificationSoundController(NotificationSoundService service) {
        this.service = service;
    }

    @GetMapping("/sounds")
    @Operation(summary = "Configuración de sonidos (sin los audios)")
    public List<NotificationSoundResponse> listar() {
        return service.listar();
    }

    @GetMapping("/sounds/{scope}/{key}/audio")
    @Operation(summary = "Audio propio de una urgencia o de un tipo")
    public ResponseEntity<byte[]> audio(@PathVariable String scope, @PathVariable String key) {
        NotificationSound s = service.audio(scope, key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(s.getMimeSound()))
                // El cliente pide con ?v=<updatedAt>: un audio nuevo cambia la URL.
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(s.getAudioSound());
    }

    @PutMapping("/admin/sounds/{scope}/{key}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Cambiar la urgencia de un tipo de notificación")
    public NotificationSoundResponse cambiarUrgencia(@PathVariable String scope, @PathVariable String key,
                                                     @RequestBody Map<String, String> cuerpo) {
        return service.cambiarUrgencia(scope, key, cuerpo == null ? null : cuerpo.get("urgency"));
    }

    @PostMapping(value = "/admin/sounds/{scope}/{key}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Subir un audio propio (MP3, WAV u OGG, máximo 1 MB)")
    public NotificationSoundResponse subirAudio(@PathVariable String scope, @PathVariable String key,
                                                @RequestParam("file") MultipartFile file) {
        return service.subirAudio(scope, key, file);
    }

    @DeleteMapping("/admin/sounds/{scope}/{key}/audio")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Quitar el audio propio y volver al tono")
    public ResponseEntity<Void> quitarAudio(@PathVariable String scope, @PathVariable String key) {
        service.quitarAudio(scope, key);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/sounds/{scope}/{key}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Restablecer el sonido de una urgencia o de un tipo")
    public ResponseEntity<Void> restablecer(@PathVariable String scope, @PathVariable String key) {
        service.restablecer(scope, key);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/sounds")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Restablecer todos los sonidos")
    public ResponseEntity<Void> restablecerTodo() {
        service.restablecerTodo();
        return ResponseEntity.noContent().build();
    }
}
