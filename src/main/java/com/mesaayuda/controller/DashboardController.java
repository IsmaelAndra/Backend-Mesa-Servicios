package com.mesaayuda.controller;

import com.mesaayuda.dto.DashboardOperations;
import com.mesaayuda.dto.DashboardStats;
import com.mesaayuda.service.DashboardService;
import com.mesaayuda.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserService userService;

    @GetMapping("/stats")
    public DashboardStats stats(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.getStats(status, area, category, from, to);
    }

    /**
     * Operación en tiempo real: es una vista de supervisión. Ocultarla en la
     * interfaz no es suficiente, así que el permiso se comprueba aquí; un agente
     * que la pida directamente recibe 403.
     */
    @GetMapping("/operations")
    public ResponseEntity<?> operations() {
        if (!userService.hasRole("ADMIN") && !userService.hasRole("SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "La operación en tiempo real es solo para administradores"));
        }
        return ResponseEntity.ok(dashboardService.getOperations());
    }
}