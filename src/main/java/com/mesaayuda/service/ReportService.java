package com.mesaayuda.service;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.TicketRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private com.mesaayuda.repository.TicketSlaRepository ticketSlaRepository;

    @Autowired
    private com.mesaayuda.repository.TicketRatingRepository ratingRepository;

    /**
     * Genera un reporte Jasper en el formato indicado (PDF, EXCEL...).
     */
    public byte[] generate(String reportName, String format, LocalDate from, LocalDate to, Long typeId) {
        try {
            var resource = new ClassPathResource("reports/" + reportName + ".jrxml");
            if (!resource.exists()) {
                throw new ResourceNotFoundException("Reporte", "nombre", reportName);
            }
            JasperReport jasperReport = JasperCompileManager.compileReport(resource.getInputStream());

            java.time.OffsetDateTime start = from != null
                    ? from.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
            java.time.OffsetDateTime end = to != null
                    ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : null;

            List<Ticket> tickets = ticketRepository.findOpenTickets().stream()
                    .filter(t -> start == null || !t.getCreatedAtTicket().isBefore(start))
                    .filter(t -> end == null || t.getCreatedAtTicket().isBefore(end))
                    .filter(t -> typeId == null
                            || (t.getTypeTicket() != null && t.getTypeTicket().getIdTicketType().equals(typeId)))
                    .toList();

            var data = tickets.stream()
                    .map(t -> new java.util.LinkedHashMap<String, Object>() {{
                        put("id", t.getIdTicket());
                        put("titulo", t.getTitleTicket());
                        put("usuario", t.getUserTicket() != null ? t.getUserTicket().getCorporateEmailUser() : "");
                        put("agente", t.getAgentTicket() != null ? t.getAgentTicket().getCorporateEmailUser() : "");
                        put("area", t.getAreaTicket() != null ? t.getAreaTicket().getNameArea() : "");
                        put("categoria", t.getCategoryTicket() != null ? t.getCategoryTicket().getNameCategory() : "");
                        put("tipo", t.getTypeTicket() != null ? t.getTypeTicket().getNameTicketType() : "");
                        put("estado", t.getStatusTicket() != null ? t.getStatusTicket().getNameStatus() : "");
                        put("prioridad", t.getPriorityTicket() != null ? t.getPriorityTicket().getNamePriority() : "");
                        put("fecha", t.getCreatedAtTicket() != null ? t.getCreatedAtTicket().toLocalDate().toString() : "");
                    }})
                    .collect(java.util.stream.Collectors.toList());

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);

            Map<String, Object> params = new HashMap<>();
            params.put("TITULO", "Reporte - " + reportName);
            params.put("FECHA", LocalDate.now().toString());
            params.put("TOTAL", tickets.size());
            params.put("datos", dataSource);

            JasperPrint print = JasperFillManager.fillReport(jasperReport, params, dataSource);

            if ("EXCEL".equalsIgnoreCase(format) || "XLSX".equalsIgnoreCase(format)) {
                return exportXlsx(print);
            }
            if ("HTML".equalsIgnoreCase(format)) {
                ByteArrayOutputStream htmlOut = new ByteArrayOutputStream();
                net.sf.jasperreports.engine.export.HtmlExporter exporter =
                        new net.sf.jasperreports.engine.export.HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new net.sf.jasperreports.export.SimpleHtmlExporterOutput(htmlOut));
                exporter.exportReport();
                return htmlOut.toByteArray();
            }
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            log.error("Error generando reporte {}", reportName, e);
            throw new com.mesaayuda.exception.BadRequestException("Error generando reporte: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado en reporte {}", reportName, e);
            throw new com.mesaayuda.exception.BadRequestException("Error inesperado generando reporte");
        }
    }

    private byte[] exportXlsx(JasperPrint print) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }
}