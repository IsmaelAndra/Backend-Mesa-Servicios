package com.mesaayuda.controller;

import com.mesaayuda.dto.AccesoConfidencialResponse;
import com.mesaayuda.service.AccesoConfidencialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Acceso de emergencia a tickets confidenciales (súper admin / auditor piden, el jefe decide). */
@RestController
@RequestMapping("/tickets")
@Tag(name = "Tickets confidenciales")
public class AccesoConfidencialController {

    private final AccesoConfidencialService servicio;

    public AccesoConfidencialController(AccesoConfidencialService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/{id}/acceso-confidencial")
    @Operation(summary = "Pedir acceso de emergencia a un ticket confidencial")
    public ResponseEntity<AccesoConfidencialResponse> solicitar(@PathVariable UUID id,
                                                                @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(servicio.solicitar(id, body == null ? null : body.get("motivo")));
    }

    @GetMapping("/{id}/acceso-confidencial")
    @Operation(summary = "Mi última solicitud de acceso a este ticket")
    public ResponseEntity<AccesoConfidencialResponse> miSolicitud(@PathVariable UUID id) {
        AccesoConfidencialResponse r = servicio.miSolicitud(id);
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/accesos-confidenciales/pendientes")
    @Operation(summary = "Solicitudes de acceso que decide el jefe")
    public ResponseEntity<List<AccesoConfidencialResponse>> pendientes() {
        return ResponseEntity.ok(servicio.pendientesParaMi());
    }

    @PostMapping("/accesos-confidenciales/{accesoId}/decidir")
    @Operation(summary = "Aprobar o rechazar una solicitud de acceso")
    public ResponseEntity<AccesoConfidencialResponse> decidir(@PathVariable UUID accesoId,
                                                              @RequestBody Map<String, Object> body) {
        boolean aprobar = body != null && Boolean.TRUE.equals(body.get("aprobar"));
        String comentario = body == null || body.get("comentario") == null ? null : String.valueOf(body.get("comentario"));
        return ResponseEntity.ok(servicio.decidir(accesoId, aprobar, comentario));
    }
}
