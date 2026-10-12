package com.mesaayuda.controller;

import com.mesaayuda.dto.SolicitanteResumenResponse;
import com.mesaayuda.service.SolicitanteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Ficha 360° del solicitante.
 */
@RestController
@RequestMapping("/solicitantes")
@Tag(name = "Solicitantes", description = "Ficha 360° del solicitante")
public class SolicitanteController {

    @Autowired
    private SolicitanteService solicitanteService;

    @GetMapping("/{userId}/resumen")
    @Operation(summary = "Ficha 360° de un solicitante")
    public ResponseEntity<SolicitanteResumenResponse> resumen(@PathVariable UUID userId) {
        return ResponseEntity.ok(solicitanteService.resumen(userId));
    }
}