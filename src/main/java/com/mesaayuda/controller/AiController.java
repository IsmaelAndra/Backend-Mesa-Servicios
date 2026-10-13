package com.mesaayuda.controller;

import com.mesaayuda.dto.AiAssistRequest;
import com.mesaayuda.dto.AiAssistResponse;
import com.mesaayuda.dto.AiChatRequest;
import com.mesaayuda.dto.AiStatusResponse;
import com.mesaayuda.service.AiAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@Tag(name = "Asistente IA", description = "Asistente con IA para agentes y administradores")
public class AiController {

    @Autowired
    private AiAssistantService aiAssistantService;

    @PostMapping("/assist")
    @Operation(summary = "Acción del asistente IA (sugerir respuesta o resumen de ticket)")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPER_ADMIN')")
    public ResponseEntity<AiAssistResponse> assist(@Valid @RequestBody AiAssistRequest request) {
        String content = aiAssistantService.assist(
                request.action(), request.ticketId(), request.instructions());
        return ResponseEntity.ok(new AiAssistResponse(content));
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat libre con el asistente IA (widget flotante)")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPER_ADMIN','USER','AREA_MANAGER')")
    public ResponseEntity<AiAssistResponse> chat(@Valid @RequestBody AiChatRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = auth != null ? auth.getName() : "anonimo";
        String session = request.session() == null || request.session().isBlank()
                ? user : request.session();
        String content = aiAssistantService.chat(request.message(), session, request.context());
        return ResponseEntity.ok(new AiAssistResponse(content));
    }

    @GetMapping("/config")
    @Operation(summary = "Estado del asistente IA (habilitado, proveedor, modelo e icono)")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPER_ADMIN','USER','AREA_MANAGER')")
    public ResponseEntity<AiStatusResponse> config() {
        return ResponseEntity.ok(aiAssistantService.status());
    }
}