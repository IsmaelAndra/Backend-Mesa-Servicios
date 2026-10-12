package com.mesaayuda.controller;

import com.mesaayuda.dto.*;
import com.mesaayuda.entity.Attach;
import com.mesaayuda.service.AuditService;
import com.mesaayuda.service.RatingService;
import com.mesaayuda.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@Tag(name = "Tickets", description = "Gestión de tickets de soporte")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private AuditService auditService;

    // ============================ CONSULTA ============================

    @GetMapping
    @Operation(summary = "Buscar tickets con filtros avanzados")
    public ResponseEntity<Page<TicketResponse>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) UUID requesterId,
@RequestParam(required = false) String keyword,
        @RequestParam(required = false) UUID sucursalId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ticketService.search(status, priority, category,
                areaId, agentId, requesterId, keyword, sucursalId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener ticket por id")
    public ResponseEntity<TicketResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @GetMapping("/mine")
    @Operation(summary = "Mis tickets (usuario solicitante)")
    public ResponseEntity<List<TicketResponse>> mine() {
        return ResponseEntity.ok(ticketService.myTickets());
    }

    @GetMapping("/assigned")
    @Operation(summary = "Tickets asignados a mi (agente)")
    public ResponseEntity<List<TicketResponse>> assigned() {
        return ResponseEntity.ok(ticketService.assignedTickets());
    }

    // ============================ OPERACIONES ============================

    @PostMapping
    @Operation(summary = "Crear ticket (enrutamiento automático + SLA)")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    @PostMapping("/{id}/assign")
    @Operation(summary = "Asignar ticket a un agente o equipo")
    public ResponseEntity<TicketResponse> assign(@PathVariable UUID id,
                                                 @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(ticketService.assign(id, request));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del ticket")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(ticketService.changeStatus(id, request));
    }

    // ============================ RESPUESTAS ============================

    @PostMapping("/{id}/answers")
    @Operation(summary = "Responder ticket (usuario, agente o admin)")
    public ResponseEntity<AnswerResponse> addAnswer(@PathVariable UUID id,
                                                    @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addAnswer(id, request));
    }

    @PutMapping("/{id}/answers/{answerId}")
    @Operation(summary = "Editar un mensaje propio (primeros 15 minutos)")
    public ResponseEntity<AnswerResponse> editAnswer(@PathVariable UUID id, @PathVariable UUID answerId,
                                                     @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(ticketService.editAnswer(id, answerId, request.message()));
    }

    @DeleteMapping("/{id}/answers/{answerId}")
    @Operation(summary = "Eliminar un mensaje propio (primeros 15 minutos)")
    public ResponseEntity<Void> deleteAnswer(@PathVariable UUID id, @PathVariable UUID answerId) {
        ticketService.deleteAnswer(id, answerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/answers")
    @Operation(summary = "Listar respuestas de un ticket",
            description = "Paginadas de la más reciente a la más antigua: el chat abre por abajo y carga la anterior al subir el scroll")
    public ResponseEntity<AnswerPageResponse> answers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketService.getAnswersPage(id, page, size));
    }

    @PostMapping("/{id}/typing")
    @Operation(summary = "Avisar que el usuario actual está escribiendo en el ticket")
    public ResponseEntity<Void> typing(@PathVariable UUID id) {
        ticketService.notifyTyping(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ ADJUNTOS ============================

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir archivo adjunto a un ticket",
            description = "Si se indica answerId, el adjunto queda ligado a esa respuesta (mensaje). Un nombre solo puede usarse una vez por ticket.")
    public ResponseEntity<AttachResponse> upload(@PathVariable UUID id,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) UUID answerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toAttachResponse(ticketService.uploadAttachment(id, answerId, file)));
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "Listar adjuntos de un ticket")
    public ResponseEntity<List<AttachResponse>> attachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getAttachments(id).stream()
                .map(this::toAttachResponse)
                .toList());
    }

    // ============================ CALIFICACIÓN ============================

    @PostMapping("/{id}/rate")
    @Operation(summary = "Calificar ticket por requerimientos cumplidos")
    public ResponseEntity<RatingResponse> rate(@PathVariable UUID id,
                                               @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(id, request));
    }

    @GetMapping("/{id}/rating")
    @Operation(summary = "Ver calificación de un ticket")
    public ResponseEntity<RatingResponse> getRating(@PathVariable UUID id) {
        return ResponseEntity.ok(ratingService.getByTicket(id));
    }

    // ============================ AUDITORÍA ============================

    @GetMapping("/{id}/audit")
    @Operation(summary = "Historial/auditoría de un ticket")
    public ResponseEntity<List<AuditLogResponse>> audit(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.findByTicket(id));
    }

private AttachResponse toAttachResponse(Attach a) {
    UUID ticketId = a.getTicketAttach() != null ? a.getTicketAttach().getIdTicket() : null;
    return new AttachResponse(
            a.getIdAttach(),
            a.getFileNameAttach(),
            // Antes se devolvia storagePathAttach, que es la ruta del archivo en el
            // disco del servidor: revelaba el sistema de archivos y, como estaba en
            // una carpeta publica, bastaba con quitarse el nombre para descargar el
            // archivo. Ahora se da la ruta de descarga, que si pasa por el permiso.
            ticketId != null
                    ? "/tickets/" + ticketId + "/attachments/" + a.getIdAttach() + "/content"
                    : null,
            a.getSizeFileAttach(),
            a.getMimeTypeAttach(),
            a.getCreatedAtAttach(),
            a.getAnswerAttach() != null ? a.getAnswerAttach().getIdAnswer() : null);
  }

  public record AttachResponse(
          UUID idAttach,
          String fileNameAttach,
          String downloadUrl,
          long sizeFileAttach,
          String mimeTypeAttach,
          java.time.OffsetDateTime createdAtAttach,
          UUID answerId) {
  }
}