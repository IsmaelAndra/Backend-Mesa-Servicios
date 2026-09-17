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
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ticketService.search(status, priority, category,
                areaId, agentId, requesterId, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener ticket por id")
    public ResponseEntity<TicketResponse> findById(@PathVariable Long id) {
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
    public ResponseEntity<TicketResponse> assign(@PathVariable Long id,
                                                 @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(ticketService.assign(id, request));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Cambiar estado del ticket")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(ticketService.changeStatus(id, request));
    }

    // ============================ RESPUESTAS ============================

    @PostMapping("/{id}/answers")
    @Operation(summary = "Responder ticket (usuario, agente o admin)")
    public ResponseEntity<AnswerResponse> addAnswer(@PathVariable Long id,
                                                    @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addAnswer(id, request));
    }

    @GetMapping("/{id}/answers")
    @Operation(summary = "Listar respuestas de un ticket")
    public ResponseEntity<List<AnswerResponse>> answers(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getAnswers(id));
    }

    // ============================ ADJUNTOS ============================

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir archivo adjunto a un ticket",
            description = "Si se indica answerId, el adjunto queda ligado a esa respuesta (mensaje). Un nombre solo puede usarse una vez por ticket.")
    public ResponseEntity<AttachResponse> upload(@PathVariable Long id,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) Long answerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toAttachResponse(ticketService.uploadAttachment(id, answerId, file)));
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "Listar adjuntos de un ticket")
    public ResponseEntity<List<AttachResponse>> attachments(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getAttachments(id).stream()
                .map(this::toAttachResponse)
                .toList());
    }

    // ============================ CALIFICACIÓN ============================

    @PostMapping("/{id}/rate")
    @Operation(summary = "Calificar ticket por requerimientos cumplidos")
    public ResponseEntity<RatingResponse> rate(@PathVariable Long id,
                                               @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(id, request));
    }

    @GetMapping("/{id}/rating")
    @Operation(summary = "Ver calificación de un ticket")
    public ResponseEntity<RatingResponse> getRating(@PathVariable Long id) {
        return ResponseEntity.ok(ratingService.getByTicket(id));
    }

    // ============================ AUDITORÍA ============================

    @GetMapping("/{id}/audit")
    @Operation(summary = "Historial/auditoría de un ticket")
    public ResponseEntity<List<AuditLogResponse>> audit(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.findByTicket(id));
    }

    private AttachResponse toAttachResponse(Attach a) {
        return new AttachResponse(
                a.getIdAttach(),
                a.getFileNameAttach(),
                a.getStoragePathAttach(),
                a.getSizeFileAttach(),
                a.getMimeTypeAttach(),
                a.getCreatedAtAttach(),
                a.getAnswerAttach() != null ? a.getAnswerAttach().getIdAnswer() : null);
    }

    public record AttachResponse(
            Long idAttach,
            String fileNameAttach,
            String storagePathAttach,
            long sizeFileAttach,
            String mimeTypeAttach,
            java.time.OffsetDateTime createdAtAttach,
            Long answerId) {
    }
}