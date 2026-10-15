package com.mesaayuda.controller;

import com.mesaayuda.entity.Llamada;
import com.mesaayuda.service.TelefoniaService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Llamadas entre extensiones (central Grandstream UCM).
 *
 * <ul>
 *   <li>Mesa (sesión normal): {@code /telefonia/estado}, {@code /telefonia/tickets/{id}/llamar},
 *       {@code /telefonia/llamadas/{id}}.</li>
 *   <li>Administración (SUPER_ADMIN): {@code /admin/telefonia/**}.</li>
 *   <li>Puente (cabecera {@code X-Puente-Token}, sin sesión): {@code /telefonia/puente/**}.
 *       SecurityConfig lo deja pasar y el token lo comprueba {@link TelefoniaService}.</li>
 * </ul>
 */
@RestController
@Tag(name = "Telefonía", description = "Llamadas entre extensiones a través del puente interno")
public class TelefoniaController {

    private final TelefoniaService service;
    private final UserService userService;

    public TelefoniaController(TelefoniaService service, UserService userService) {
        this.service = service;
        this.userService = userService;
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

    // ============================ Mesa ============================

    @Operation(summary = "Si la telefonía está disponible")
    @GetMapping("/telefonia/estado")
    public Map<String, Boolean> estado() {
        return Map.of("habilitada", service.habilitada());
    }

    @Operation(summary = "Llamar al solicitante del ticket desde la extensión del agente")
    @PostMapping("/telefonia/tickets/{ticketId}/llamar")
    public LlamadaResponse llamar(@PathVariable UUID ticketId) {
        return LlamadaResponse.de(service.llamarAlSolicitante(ticketId));
    }

    @Operation(summary = "Estado de una llamada pedida")
    @GetMapping("/telefonia/llamadas/{id}")
    public LlamadaResponse llamada(@PathVariable UUID id) {
        return LlamadaResponse.de(service.estado(id));
    }

    // ============================ Administración ============================

    @Operation(summary = "Configuración de la telefonía (sin la clave de la UCM)")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/admin/telefonia")
    public TelefoniaService.ConfigVista config() {
        return service.vista();
    }

    @Operation(summary = "Guardar la configuración de la UCM")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PutMapping("/admin/telefonia")
    public TelefoniaService.ConfigVista guardar(@RequestBody TelefoniaService.ConfigRequest req) {
        return service.guardar(req, userService.getCurrentUser().getIdUser());
    }

    @Operation(summary = "Generar un token nuevo para el puente (se muestra una sola vez)")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @PostMapping("/admin/telefonia/token")
    public Map<String, String> token() {
        return Map.of("token", service.generarToken(userService.getCurrentUser().getIdUser()));
    }

    @Operation(summary = "Últimas llamadas")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/admin/telefonia/llamadas")
    public List<TelefoniaService.LlamadaVista> llamadas() {
        return service.recientes();
    }

    // ============================ Puente ============================

    private static TelefoniaService.Latido latido(HttpServletRequest req, String token, String version, String ucm) {
        return new TelefoniaService.Latido(token, req.getRemoteAddr(), version, ucm);
    }

    @Operation(summary = "Puente: configuración de la UCM")
    @GetMapping("/telefonia/puente/config")
    public TelefoniaService.ConfigPuente configPuente(HttpServletRequest req,
            @RequestHeader(value = "X-Puente-Token", required = false) String token,
            @RequestHeader(value = "X-Puente-Version", required = false) String version,
            @RequestHeader(value = "X-Puente-Ucm", required = false) String ucm) {
        return service.configParaPuente(latido(req, token, version, ucm));
    }

    @Operation(summary = "Puente: recoger las llamadas pendientes")
    @GetMapping("/telefonia/puente/pendientes")
    public List<LlamadaResponse> pendientes(HttpServletRequest req,
            @RequestHeader(value = "X-Puente-Token", required = false) String token,
            @RequestHeader(value = "X-Puente-Version", required = false) String version,
            @RequestHeader(value = "X-Puente-Ucm", required = false) String ucm) {
        return service.recogerPendientes(latido(req, token, version, ucm)).stream().map(LlamadaResponse::de).toList();
    }

    @Operation(summary = "Puente: informar el resultado de una llamada")
    @PostMapping("/telefonia/puente/llamadas/{id}/resultado")
    public ResponseEntity<Void> resultado(HttpServletRequest req,
            @RequestHeader(value = "X-Puente-Token", required = false) String token,
            @RequestHeader(value = "X-Puente-Version", required = false) String version,
            @PathVariable UUID id,
            @RequestBody ResultadoRequest cuerpo) {
        service.informarResultado(latido(req, token, version, null), id,
                cuerpo != null && cuerpo.ok(), cuerpo == null ? null : cuerpo.error());
        return ResponseEntity.noContent().build();
    }
}
