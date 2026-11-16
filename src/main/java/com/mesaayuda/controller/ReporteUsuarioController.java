package com.mesaayuda.controller;

import com.mesaayuda.reportes.ConstructorConsulta;
import com.mesaayuda.reportes.ReporteUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reportes hechos desde el modal (Fase 2). Roles: súper admin, jefes y auditor (SecurityConfig). */
@RestController
@RequestMapping("/reportes-personalizados")
@Tag(name = "Reportes personalizados")
public class ReporteUsuarioController {

    private final ReporteUsuarioService servicio;

    public ReporteUsuarioController(ReporteUsuarioService servicio) {
        this.servicio = servicio;
    }

    public record GuardarRequest(String nombre, String descripcion, String icono, String color,
                                 ConstructorConsulta.Definicion definicion) {
    }

    public record VistaPreviaRequest(ConstructorConsulta.Definicion definicion,
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
    }

    @GetMapping("/catalogo")
    @Operation(summary = "Tablas, columnas y filtros del constructor")
    public List<Map<String, Object>> catalogo() {
        return servicio.catalogo();
    }

    @GetMapping
    @Operation(summary = "Reportes guardados")
    public List<ReporteUsuarioService.ReporteResponse> listar() {
        return servicio.listar();
    }

    @PostMapping
    @Operation(summary = "Guardar un reporte nuevo")
    public ReporteUsuarioService.ReporteResponse crear(@RequestBody GuardarRequest r) {
        return servicio.guardar(null, r.nombre(), r.descripcion(), r.icono(), r.color(), r.definicion());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un reporte")
    public ReporteUsuarioService.ReporteResponse editar(@PathVariable UUID id, @RequestBody GuardarRequest r) {
        return servicio.guardar(id, r.nombre(), r.descripcion(), r.icono(), r.color(), r.definicion());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un reporte")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/vista-previa")
    @Operation(summary = "Vista previa de una definición sin guardar")
    public ReporteUsuarioService.VistaPrevia vistaPrevia(@RequestBody VistaPreviaRequest r) {
        return servicio.vistaPrevia(r.definicion(), r.desde(), r.hasta());
    }

    @GetMapping("/{id}/vista-previa")
    @Operation(summary = "Vista previa de un reporte guardado")
    public ReporteUsuarioService.VistaPrevia vistaPreviaGuardado(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.vistaPrevia(id, desde, hasta);
    }

    @GetMapping("/{id}/exportar")
    @Operation(summary = "Exportar un reporte guardado a PDF o Excel")
    public ResponseEntity<byte[]> exportar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "PDF") String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String orientacion) {
        boolean excel = "EXCEL".equalsIgnoreCase(formato) || "XLSX".equalsIgnoreCase(formato);
        byte[] datos = servicio.exportar(id, excel ? "EXCEL" : "PDF", desde, hasta, orientacion);
        // Solo ASCII en el nombre del archivo: con tildes la cabecera se rompe en algunos navegadores.
        String base = java.text.Normalizer.normalize(servicio.nombre(id), java.text.Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '_');
        String archivo = (base.isEmpty() ? "reporte" : base) + (excel ? ".xlsx" : ".pdf");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .contentType(excel
                        ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : MediaType.APPLICATION_PDF)
                .body(datos);
    }
}
