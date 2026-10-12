package com.mesaayuda.controller;

import com.mesaayuda.entity.ReportConfig;
import com.mesaayuda.service.CustomReportService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reportes", description = "Generación de reportes con JasperReports")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class ReportController {

    private static final java.util.Map<String, String> MIME_BY_FORMAT = java.util.Map.of(
            "PDF", MediaType.APPLICATION_PDF_VALUE,
            "EXCEL", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "HTML", MediaType.TEXT_HTML_VALUE
    );

    @Autowired
    private CustomReportService customReportService;

    @Autowired
    private UserService userService;

    @Autowired
    private com.mesaayuda.service.SystemAuditService systemAuditService;

    @GetMapping("/disponibles")
    @Operation(summary = "Listar los reportes disponibles",
            description = "El frontend consume esta lista para no tener los reportes escritos en el código")
    public ResponseEntity<List<Map<String, Object>>> disponibles() {
        List<ReportConfig> activos = customReportService.list(true);
        List<Map<String, Object>> salida = activos.stream()
                .map(r -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", r.getIdReport());
                    m.put("nombre", r.getNameReport());
                    m.put("titulo", r.getTitleReport());
                    m.put("descripcion", r.getDescriptionReport());
                    m.put("icono", r.getIconReport());
                    m.put("color", r.getColorReport());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(salida);
    }

    @GetMapping("/custom/{id}")
    @Operation(summary = "Generar un reporte por su id (PDF/EXCEL/HTML)",
            description = "Las columnas las genera la consulta SQL definida en Reportes Personalizados. "
                    + "orientacion: H (apaisada, más columnas) o V (vertical)")
    public ResponseEntity<byte[]> generateCustom(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
@RequestParam(required = false) UUID typeId,
              @RequestParam(required = false, defaultValue = "H") String orientacion,
              @RequestParam(required = false) UUID sucursalId) {

          com.mesaayuda.entity.User actual = userService.getCurrentUser();
          UUID userId = actual.getIdUser();
          byte[] data = customReportService.generate(id, format, from, to, typeId, userId, orientacion, sucursalId);
        // Un reporte es una extracción masiva de datos de la mesa: se deja constancia
        // de quién lo sacó, cuál y con qué filtros.
        systemAuditService.record("REPORT_GENERATE", "ReportConfig", id, actual.getCorporateEmailUser(),
                "formato=" + format + " desde=" + from + " hasta=" + to + " sede=" + sucursalId);
        String mime = MIME_BY_FORMAT.getOrDefault(format.toUpperCase(), MediaType.APPLICATION_PDF_VALUE);
        String ext = "PDF".equalsIgnoreCase(format) ? "pdf"
                : ("EXCEL".equalsIgnoreCase(format) || "XLSX".equalsIgnoreCase(format)) ? "xlsx" : "html";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte_" + id + "_" + LocalDate.now() + "." + ext + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }
}