package com.mesaayuda.controller;

import com.mesaayuda.entity.Feriado;
import com.mesaayuda.entity.HorarioLaboral;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.FeriadoRepository;
import com.mesaayuda.repository.HorarioLaboralRepository;
import com.mesaayuda.service.CalendarioLaboralService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Horario laboral y feriados del SLA (V49). Solo el SUPER_ADMIN los cambia
 * (pestaña "Horario y feriados" de SLA); el AUDITOR los ve.
 */
@RestController
@RequestMapping("/admin")
public class HorarioLaboralController {

    private final HorarioLaboralRepository horarios;
    private final FeriadoRepository feriados;
    private final CalendarioLaboralService calendario;

    public HorarioLaboralController(HorarioLaboralRepository horarios, FeriadoRepository feriados,
                                    CalendarioLaboralService calendario) {
        this.horarios = horarios;
        this.feriados = feriados;
        this.calendario = calendario;
    }

    public record DiaRequest(Integer dia, String inicio, String fin, Boolean activo) {
    }

    public record FeriadoRequest(LocalDate fecha, String nombre, Boolean anual) {
    }

    @GetMapping("/horario-laboral")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR', 'ROLE_AREA_MANAGER')")
    public List<HorarioLaboral> horario() {
        return horarios.findAllByOrderByDiaHorarioAsc();
    }

    /** Guarda los 7 días de una vez. */
    @PutMapping("/horario-laboral")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Transactional
    public List<HorarioLaboral> guardarHorario(@RequestBody List<DiaRequest> dias) {
        if (dias == null || dias.isEmpty()) {
            throw new BadRequestException("Envíe el horario de los días");
        }
        boolean alguno = false;
        for (DiaRequest d : dias) {
            if (d.dia() == null || d.dia() < 1 || d.dia() > 7) {
                throw new BadRequestException("Día no válido");
            }
            LocalTime inicio = hora(d.inicio());
            LocalTime fin = hora(d.fin());
            if (!fin.isAfter(inicio)) {
                throw new BadRequestException("La hora de salida debe ser posterior a la de entrada");
            }
            HorarioLaboral h = horarios.findByDiaHorario(d.dia()).orElseGet(() -> {
                HorarioLaboral n = new HorarioLaboral();
                n.setDiaHorario(d.dia());
                return n;
            });
            h.setInicioHorario(inicio);
            h.setFinHorario(fin);
            h.setActivoHorario(!Boolean.FALSE.equals(d.activo()));
            alguno |= Boolean.TRUE.equals(h.getActivoHorario());
            horarios.save(h);
        }
        if (!alguno) {
            throw new BadRequestException("Debe haber al menos un día laborable");
        }
        calendario.olvidar();
        return horarios.findAllByOrderByDiaHorarioAsc();
    }

    @GetMapping("/feriados")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR', 'ROLE_AREA_MANAGER')")
    public List<Feriado> feriados() {
        return feriados.findAllByOrderByFechaFeriadoAsc();
    }

    @PostMapping("/feriados")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public Feriado crearFeriado(@RequestBody FeriadoRequest r) {
        Feriado f = new Feriado();
        aplicar(f, r);
        Feriado guardado = feriados.save(f);
        calendario.olvidar();
        return guardado;
    }

    @PutMapping("/feriados/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public Feriado editarFeriado(@PathVariable UUID id, @RequestBody FeriadoRequest r) {
        Feriado f = feriados.findById(id).orElseThrow(() -> new ResourceNotFoundException("Feriado", "id", id));
        aplicar(f, r);
        Feriado guardado = feriados.save(f);
        calendario.olvidar();
        return guardado;
    }

    @DeleteMapping("/feriados/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> eliminarFeriado(@PathVariable UUID id) {
        feriados.deleteById(id);
        calendario.olvidar();
        return ResponseEntity.noContent().build();
    }

    private void aplicar(Feriado f, FeriadoRequest r) {
        if (r == null || r.fecha() == null) {
            throw new BadRequestException("Elija la fecha del feriado");
        }
        String nombre = r.nombre() == null ? "" : r.nombre().strip();
        if (nombre.isEmpty() || nombre.length() > 120) {
            throw new BadRequestException("Escriba el nombre del feriado (máximo 120 caracteres)");
        }
        boolean otra = feriados.findAll().stream()
                .anyMatch(x -> x.getFechaFeriado().equals(r.fecha()) && !x.getIdFeriado().equals(f.getIdFeriado()));
        if (otra) {
            throw new BadRequestException("Ya hay un feriado en esa fecha");
        }
        f.setFechaFeriado(r.fecha());
        f.setNombreFeriado(nombre);
        f.setAnualFeriado(Boolean.TRUE.equals(r.anual()));
    }

    private static LocalTime hora(String s) {
        try {
            return LocalTime.parse(s == null ? "" : s.trim());
        } catch (RuntimeException e) {
            throw new BadRequestException("Hora no válida (use HH:mm)");
        }
    }
}
