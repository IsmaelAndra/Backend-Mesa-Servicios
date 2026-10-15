package com.mesaayuda.controller;

import com.mesaayuda.entity.Llamada;
import com.mesaayuda.service.TelefoniaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Llamadas entre extensiones (central Grandstream UCM).
 *
 * <p>Dos públicos distintos:
 * <ul>
 *   <li>La mesa (sesión normal): {@code /telefonia/estado}, {@code /telefonia/tickets/{id}/llamar}
 *       y {@code /telefonia/llamadas/{id}}.</li>
 *   <li>El puente de telefonía (cabecera {@code X-Puente-Token}, sin sesión):
 *       {@code /telefonia/puente/**}. SecurityConfig lo deja pasar y el token lo
 *       comprueba {@link TelefoniaService}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/telefonia")
@Tag(name = "Telefonía", description = "Llamadas entre extensiones a través del puente interno")
public class TelefoniaController {

    private final TelefoniaService service;

    public TelefoniaController(TelefoniaService service) {
        this.service = service;
    }

    /** Respuesta mínima de una llamada (lo que necesitan la ficha y el puente). */
    public record LlamadaResponse(UUID id, String origen, String destino, String estado, String error,
                                  OffsetDateTime creada) {
        static LlamadaResponse de(Llamada l) {
            return new LlamadaResponse(l.getIdLlamada(), l.getCallerExtension(), l.getCalleeExtension(),
                    l.getStatusLlamada(), l.getErrorLlamada(), l.getCreatedAtLlamada());
        }
    }

    public record ResultadoRequest(boolean ok, String error) {}

    @Operation(summary = "Si la telefonía está disponible (hay puente configurado)")
    @GetMapping("/estado")
    public Map<String, Boolean> estado() {
        return Map.of("habilitada", service.habilitada());
    }

    @Operation(summary = "Llamar al solicitante del ticket desde la extensión del agente")
    @PostMapping("/tickets/{ticketId}/llamar")
    public LlamadaResponse llamar(@PathVariable UUID ticketId) {
        return LlamadaResponse.de(service.llamarAlSolicitante(ticketId));
    }

    @Operation(summary = "Estado de una llamada pedida")
    @GetMapping("/llamadas/{id}")
    public LlamadaResponse llamada(@PathVariable UUID id) {
        return LlamadaResponse.de(service.estado(id));
    }

    // ============================ Puente ============================

    @Operation(summary = "Puente: recoger las llamadas pendientes")
    @GetMapping("/puente/pendientes")
    public List<LlamadaResponse> pendientes(@RequestHeader(value = "X-Puente-Token", required = false) String token) {
        return service.recogerPendientes(token).stream().map(LlamadaResponse::de).toList();
    }

    @Operation(summary = "Puente: informar el resultado de una llamada")
    @PostMapping("/puente/llamadas/{id}/resultado")
    public ResponseEntity<Void> resultado(@RequestHeader(value = "X-Puente-Token", required = false) String token,
                                          @PathVariable UUID id,
                                          @RequestBody ResultadoRequest cuerpo) {
        service.informarResultado(token, id, cuerpo != null && cuerpo.ok(), cuerpo == null ? null : cuerpo.error());
        return ResponseEntity.noContent().build();
    }
}
