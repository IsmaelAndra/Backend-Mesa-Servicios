package com.mesaayuda.controller;

import com.mesaayuda.service.ReportService;
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
    private ReportService reportService;

    @GetMapping("/{reportName}")
    @Operation(summary = "Generar reporte (PDF/EXCEL/HTML)", description = "reportName: tickets, sla, agentes, calificaciones. Filtros: from, to y typeId (tipo de ticket)")
    public ResponseEntity<byte[]> generate(
            @PathVariable String reportName,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long typeId) {

        byte[] data = reportService.generate(reportName, format, from, to, typeId);
        String mime = MIME_BY_FORMAT.getOrDefault(format.toUpperCase(), MediaType.APPLICATION_PDF_VALUE);
        String ext = "PDF".equalsIgnoreCase(format) ? "pdf"
                : ("EXCEL".equalsIgnoreCase(format) || "XLSX".equalsIgnoreCase(format)) ? "xlsx" : "html";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + reportName + "_" + LocalDate.now() + "." + ext + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }
}