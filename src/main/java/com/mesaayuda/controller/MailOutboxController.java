package com.mesaayuda.controller;

import com.mesaayuda.dto.MailOutboxItemResponse;
import com.mesaayuda.dto.MailOutboxResumenResponse;
import com.mesaayuda.service.MailOutboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Panel de la cola de correo. Solo SUPER_ADMIN. */
@RestController
@RequestMapping("/admin/mail-outbox")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
@Tag(name = "Cola de correo", description = "Estado del envío de correos por Microsoft Graph")
public class MailOutboxController {

    private final MailOutboxService service;

    public MailOutboxController(MailOutboxService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Indicadores y gráficas de la cola")
    public MailOutboxResumenResponse resumen() {
        return service.resumen();
    }

    @GetMapping
    @Operation(summary = "Correos de la cola (filtro por estado y búsqueda por destinatario o asunto)")
    public Page<MailOutboxItemResponse> listar(@RequestParam(required = false) String estado,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(estado, q, pagina, tamano);
    }

    @PostMapping("/{id}/reintentar")
    @Operation(summary = "Volver a encolar un correo fallido o cancelado")
    public MailOutboxItemResponse reintentar(@PathVariable UUID id) {
        return service.reintentar(id);
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Descartar un correo pendiente o fallido")
    public MailOutboxItemResponse cancelar(@PathVariable UUID id) {
        return service.cancelar(id);
    }

    @PostMapping("/reintentar-fallidos")
    @Operation(summary = "Volver a encolar todos los fallidos")
    public Map<String, Integer> reintentarFallidos() {
        return Map.of("reencolados", service.reintentarFallidos());
    }
}
