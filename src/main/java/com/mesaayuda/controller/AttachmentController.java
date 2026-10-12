package com.mesaayuda.controller;

import com.mesaayuda.entity.Attach;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.AttachRepository;
import com.mesaayuda.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.core.io.Resource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Descarga de adjuntos de ticket.
 *
 * Antes los archivos se servian como recursos estaticos con
 * /uploads/** en permitAll: cualquier persona con la URL podia descargar el PDF
 * o la captura de cualquier ticket, sin iniciar sesion y sin comprobar nada.
 * Aqui la comprobacion es la de siempre para el resto del ticket: titular,
 * agente asignado o administrador, y además que el adjunto no pertenezca a una
 * nota interna cuando quien pregunta no es parte del personal.
 */
@RestController
@RequestMapping("/tickets")
public class AttachmentController {

    private final TicketService ticketService;
    private final AttachRepository attachRepository;

    public AttachmentController(TicketService ticketService, AttachRepository attachRepository) {
        this.ticketService = ticketService;
        this.attachRepository = attachRepository;
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}/content")
    @Operation(summary = "Descargar el contenido de un adjunto",
            description = "Exige poder leer el ticket. Si el archivo está ligado a una nota interna, "
                    + "solo el personal con acceso puede descargarlo.")
    public ResponseEntity<Resource> download(@PathVariable UUID ticketId,
                                              @PathVariable UUID attachmentId) {
        // getAttachments ya aplica el permiso de lectura del ticket y filtra las
        // notas internas para quien no es personal. Reutilizarlo evita que la
        // descarga se convierta en un camino paralelo que se oublide de filtrar.
        boolean permitido = ticketService.getAttachments(ticketId).stream()
                .anyMatch(a -> a.getIdAttach().equals(attachmentId));
        if (!permitido) {
            // Se responde 404 y no 403 a propósito: no se confirma la existencia
            // de un archivo al que el usuario no puede acceder.
            throw new ResourceNotFoundException("Adjunto", "id", attachmentId);
        }

        Attach adj = attachRepository.findById(attachmentId)
                .filter(a -> !Boolean.TRUE.equals(a.getDeletedAttach()))
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", "id", attachmentId));

        Resource recurso = ticketService.abrirAdjunto(adj);
        if (recurso == null) {
            throw new ResourceNotFoundException("Archivo del adjunto", "id", attachmentId);
        }

String nombre = adj.getFileNameAttach() == null || adj.getFileNameAttach().isBlank()
                ? "adjunto"
                : adj.getFileNameAttach();
        // El nombre lo elige quien sube el archivo, asi que puede traer acentos, comillas
        // o saltos de linea. Va URL-encoded en filename* (UTF-8) y en una version
        // ASCII en filename, que es el parametro que usan los clientes antiguos.
        String codificado = URLEncoder.encode(nombre, StandardCharsets.UTF_8).replace("+", "%20");
        String ascii = nombre.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");

        MediaType tipo = mediaTypeDe(adj.getMimeTypeAttach());

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + codificado)
                // El archivo lo genera la aplicación y no debe quedar cacheado en
                // proxies intermedios: un adjunto borrado dejaría de estar, pero su
                // contenido seguiría en una caché pública.
.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(recurso);
    }

    /**
     * El MIME viene de la base de datos y en el alta lo rellena el cliente, asi
     * que puede no ser un tipo valido. Un parseo fallido no debe tumbar la
     * descarga: se cae a octet-stream, que es lo que el navegador espera cuando
     * no sabe que es el archivo.
     */
    private MediaType mediaTypeDe(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mime);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
