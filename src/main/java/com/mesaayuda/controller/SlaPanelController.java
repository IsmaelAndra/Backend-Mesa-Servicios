package com.mesaayuda.controller;

import com.mesaayuda.service.SlaPanelService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel de SLA en tiempo real (V49): jefe de área (su área), SUPER_ADMIN y AUDITOR (todo). */
@RestController
public class SlaPanelController {

    private final SlaPanelService servicio;

    public SlaPanelController(SlaPanelService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/tickets/sla/panel")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_AREA_MANAGER', 'ROLE_AUDITOR')")
    public SlaPanelService.Panel panel() {
        return servicio.panel();
    }
}
