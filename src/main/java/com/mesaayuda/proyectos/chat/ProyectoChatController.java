package com.mesaayuda.proyectos.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chat grupal de los proyectos (V39). */
@RestController
@RequestMapping("/proyectos")
@Tag(name = "Chat de proyectos", description = "Mensajes y archivos del equipo de cada proyecto")
public class ProyectoChatController {

    private final ProyectoChatService chat;

    public ProyectoChatController(ProyectoChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/mensajes/no-leidos")
    @Operation(summary = "Mensajes sin leer por proyecto")
    public Map<UUID, Long> noLeidos() {
        return chat.noLeidos();
    }

    @GetMapping("/{id}/chat")
    @Operation(summary = "Mensajes del chat (los más recientes primero; antesDe para cargar los anteriores)")
    public List<ProyectoChatService.MensajeVista> listar(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime antesDe,
            @RequestParam(defaultValue = "50") int tam) {
        return chat.listar(id, antesDe, tam);
    }

    @PostMapping(value = "/{id}/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enviar un mensaje con o sin archivos (hasta 5, 10 MB cada uno)")
    public ProyectoChatService.MensajeVista enviar(@PathVariable UUID id,
                                                    @RequestParam(value = "texto", required = false) String texto,
                                                    @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos,
                                                    @RequestParam(value = "menciones", required = false) List<UUID> menciones) {
        return chat.enviar(id, texto, archivos, menciones);
    }

    /** Personas que se pueden mencionar con @ (los participantes del chat). */
    @GetMapping("/{id}/chat/participantes")
    public List<ProyectoChatService.Participante> participantes(@PathVariable UUID id) {
        return chat.participantes(id);
    }

    @PostMapping("/{id}/chat/leido")
    @Operation(summary = "Marcar el chat como leído")
    public ResponseEntity<Void> leido(@PathVariable UUID id) {
        chat.marcarLeido(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/chat/adjuntos/{adjuntoId}")
    @Operation(summary = "Descargar un archivo del chat (con permiso del proyecto)")
    public ResponseEntity<byte[]> adjunto(@PathVariable UUID id, @PathVariable UUID adjuntoId) {
        ProyectoChatService.Descarga d = chat.descargar(id, adjuntoId);
        boolean imagen = d.mime().startsWith("image/");
        ContentDisposition cd = (imagen ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(d.nombre(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.mime()))
                .body(d.datos());
    }
}
