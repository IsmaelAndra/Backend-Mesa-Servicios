package com.mesaayuda.controller;

import com.mesaayuda.dto.CalendarEventResponse;
import com.mesaayuda.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/calendar")
@PreAuthorize("hasAnyRole('AGENT','ADMIN','SUPER_ADMIN')")
@Tag(name = "Calendario", description = "Eventos del calendario del agente (tareas + tickets asignados)")
public class CalendarController {

    @Autowired
    private CalendarService calendarService;

    @GetMapping("/events")
    @Operation(summary = "Eventos del calendario en un rango de fechas", description = "Combina tareas propias y tickets asignados")
    public ResponseEntity<List<CalendarEventResponse>> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return ResponseEntity.ok(calendarService.events(start, end));
    }
}