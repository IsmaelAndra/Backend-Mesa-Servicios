package com.mesaayuda.controller;

import com.mesaayuda.dto.QuickReplyRequest;
import com.mesaayuda.dto.QuickReplyResponse;
import com.mesaayuda.service.QuickReplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Respuestas rápidas del chat. Solo personal (agente o administrador): el
 * filtro de rol está en SecurityConfig, a nivel de URL.
 */
@RestController
@RequestMapping("/quick-replies")
@Tag(name = "Respuestas rápidas", description = "Textos guardados para el chat de los tickets")
public class QuickReplyController {

    private final QuickReplyService service;

    public QuickReplyController(QuickReplyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Respuestas rápidas propias y compartidas")
    public List<QuickReplyResponse> listar() {
        return service.listar();
    }

    @PostMapping
    @Operation(summary = "Crear una respuesta rápida")
    public ResponseEntity<QuickReplyResponse> crear(@Valid @RequestBody QuickReplyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una respuesta rápida propia (o cualquiera, si es administrador)")
    public QuickReplyResponse actualizar(@PathVariable UUID id, @Valid @RequestBody QuickReplyRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una respuesta rápida")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
