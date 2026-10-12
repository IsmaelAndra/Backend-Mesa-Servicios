package com.mesaayuda.controller;

import com.mesaayuda.entity.ReportConfig;
import com.mesaayuda.service.CustomReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/reports")
@Tag(name = "Reportes personalizados", description = "Reportes creados por consulta SQL (SUPER_ADMIN)")
public class ReportConfigController {

    @Autowired
    private CustomReportService reportService;

    @GetMapping
    @Operation(summary = "Listar reportes personalizados (SUPER_ADMIN)")
    public ResponseEntity<List<ReportConfig>> list() {
        // Antes esta lectura permitía a ADMIN mientras que crear, editar y borrar
        // exigían SUPER_ADMIN. Consecuencia concreta: la respuesta incluye el
        // querySql de cada reporte, o sea las consultas contra la base de datos
        // de la organización, escritas y afinadas por quien diseñó el reporte.
        // Un ADMIN no debería poder leerlas.
        checkSuperAdmin();
        return ResponseEntity.ok(reportService.list(false));
    }

    @PostMapping
    @Operation(summary = "Crear reporte personalizado (SUPER_ADMIN)")
    public ResponseEntity<ReportConfig> create(@RequestBody Map<String, Object> body) {
        checkSuperAdmin();
        ReportConfig saved = reportService.save(null,
                value(body, "name"), value(body, "title"), value(body, "description"),
                value(body, "querySql"), bool(body.get("active")), value(body, "icon"), value(body, "color"));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar reporte personalizado (SUPER_ADMIN)")
    public ResponseEntity<ReportConfig> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        checkSuperAdmin();
        ReportConfig saved = reportService.save(id,
                value(body, "name"), value(body, "title"), value(body, "description"),
                value(body, "querySql"), bool(body.get("active")), value(body, "icon"), value(body, "color"));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar reporte personalizado (SUPER_ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        checkSuperAdmin();
        reportService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ HELPERS ============================

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    // isAdmin() y checkAdminOrSuperAdmin() quedan sin usar: todos los endpoints de
    // este controlador son de SUPER_ADMIN. Se pueden borrar sin miedo.
    private void checkSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere rol SUPER_ADMIN");
        }
    }

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private Boolean bool(Object v) {
        return v == null ? null : Boolean.valueOf(v.toString());
    }
}