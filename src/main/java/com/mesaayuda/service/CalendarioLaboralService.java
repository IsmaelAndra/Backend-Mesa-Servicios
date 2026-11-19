package com.mesaayuda.service;

import com.mesaayuda.entity.Feriado;
import com.mesaayuda.entity.HorarioLaboral;
import com.mesaayuda.repository.FeriadoRepository;
import com.mesaayuda.repository.HorarioLaboralRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Horario laboral y feriados para el SLA (V49).
 *
 * <p>Una política con horario laboral solo cuenta los minutos dentro del
 * horario de cada día de la semana y fuera de los feriados: un plazo de 4 h
 * abierto el viernes a las 16:00 (horario 08:00–17:00) vence el lunes a las
 * 11:00. Todo se calcula en la hora de Ecuador (America/Guayaquil).
 *
 * <p>El horario y los feriados se leen de la base y se guardan en memoria un
 * minuto (el planificador de SLA los consulta en cada pasada); al guardarlos
 * desde la pantalla se olvida la copia.
 */
@Service
public class CalendarioLaboralService {

    public static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    /** Tope de días a recorrer: evita un bucle sin fin si no hay ningún día laborable. */
    private static final int MAX_DIAS = 3660;

    private final HorarioLaboralRepository horarios;
    private final FeriadoRepository feriados;

    private volatile Calendario cache;
    private volatile Instant cacheHasta = Instant.EPOCH;

    public CalendarioLaboralService(HorarioLaboralRepository horarios, FeriadoRepository feriados) {
        this.horarios = horarios;
        this.feriados = feriados;
    }

    /** Horario de un día: [inicio, fin). */
    record Tramo(LocalTime inicio, LocalTime fin) {
    }

    /** Foto del horario y los feriados (inmutable). Paquete para las pruebas. */
    record Calendario(Map<Integer, Tramo> porDia, Set<LocalDate> fechas, Set<String> anuales) {

        boolean esFeriado(LocalDate d) {
            return fechas.contains(d) || anuales.contains(d.getMonthValue() + "-" + d.getDayOfMonth());
        }

        /** Horario de ese día, o null si no se trabaja (día inactivo o feriado). */
        Tramo tramo(LocalDate d) {
            return esFeriado(d) ? null : porDia.get(d.getDayOfWeek().getValue());
        }

        boolean sinDiasLaborables() {
            return porDia.isEmpty();
        }
    }

    /** Se llama al guardar horario o feriados. */
    public void olvidar() {
        cacheHasta = Instant.EPOCH;
    }

    Calendario calendario() {
        Instant ahora = Instant.now();
        Calendario c = cache;
        if (c != null && ahora.isBefore(cacheHasta)) {
            return c;
        }
        Map<Integer, Tramo> porDia = new HashMap<>();
        for (HorarioLaboral h : horarios.findAll()) {
            if (Boolean.TRUE.equals(h.getActivoHorario()) && h.getFinHorario().isAfter(h.getInicioHorario())) {
                porDia.put(h.getDiaHorario(), new Tramo(h.getInicioHorario(), h.getFinHorario()));
            }
        }
        Set<LocalDate> fechas = new HashSet<>();
        Set<String> anuales = new HashSet<>();
        for (Feriado f : feriados.findAll()) {
            if (Boolean.TRUE.equals(f.getAnualFeriado())) {
                anuales.add(f.getFechaFeriado().getMonthValue() + "-" + f.getFechaFeriado().getDayOfMonth());
            } else {
                fechas.add(f.getFechaFeriado());
            }
        }
        c = new Calendario(porDia, fechas, anuales);
        cache = c;
        cacheHasta = ahora.plusSeconds(60);
        return c;
    }

    /** Suma minutos hábiles a un instante (con el horario de la base). */
    public OffsetDateTime sumarMinutosHabiles(OffsetDateTime desde, long minutos) {
        return sumar(calendario(), desde, minutos);
    }

    /** Minutos hábiles entre dos instantes (0 si b no es posterior a a). */
    public long minutosHabilesEntre(OffsetDateTime a, OffsetDateTime b) {
        return entre(calendario(), a, b);
    }

    /**
     * Vencimiento de un plazo de SLA en horario laboral. Los días completos del
     * plazo son días hábiles ("2 d" = dos jornadas, no 48 horas de oficina que
     * serían más de cinco días); lo que sobra, minutos hábiles. "8 h" sigue
     * siendo ocho horas de oficina.
     */
    public OffsetDateTime sumarPlazo(OffsetDateTime desde, long minutos) {
        Calendario cal = calendario();
        return sumar(cal, desde, aMinutosHabiles(cal, minutos));
    }

    /** Días completos del plazo × jornada media + el resto en minutos. */
    static long aMinutosHabiles(Calendario cal, long minutos) {
        if (minutos < MINUTOS_DIA || cal.sinDiasLaborables()) {
            return minutos;
        }
        long jornada = Math.round(cal.porDia().values().stream()
                .mapToLong(t -> Duration.between(t.inicio(), t.fin()).toMinutes())
                .average().orElse(MINUTOS_DIA));
        return (minutos / MINUTOS_DIA) * jornada + (minutos % MINUTOS_DIA);
    }

    private static final long MINUTOS_DIA = 1440;

    static OffsetDateTime sumar(Calendario cal, OffsetDateTime desde, long minutos) {
        if (minutos <= 0) {
            return desde;
        }
        if (cal.sinDiasLaborables()) {
            return desde.plusMinutes(minutos); // sin horario configurado: 24 h
        }
        ZonedDateTime t = desde.atZoneSameInstant(ZONA);
        long restantes = minutos;
        for (int i = 0; i < MAX_DIAS; i++) {
            LocalDate dia = t.toLocalDate();
            Tramo tramo = cal.tramo(dia);
            if (tramo != null) {
                ZonedDateTime inicio = dia.atTime(tramo.inicio()).atZone(ZONA);
                ZonedDateTime fin = dia.atTime(tramo.fin()).atZone(ZONA);
                if (t.isBefore(inicio)) {
                    t = inicio;
                }
                if (t.isBefore(fin)) {
                    long disponibles = Duration.between(t, fin).toMinutes();
                    if (restantes <= disponibles) {
                        return t.plusMinutes(restantes).toOffsetDateTime().withOffsetSameInstant(desde.getOffset());
                    }
                    restantes -= disponibles;
                }
            }
            t = dia.plusDays(1).atStartOfDay(ZONA);
        }
        return desde.plusMinutes(minutos);
    }

    static long entre(Calendario cal, OffsetDateTime a, OffsetDateTime b) {
        if (a == null || b == null || !b.isAfter(a)) {
            return 0;
        }
        if (cal.sinDiasLaborables()) {
            return Duration.between(a, b).toMinutes();
        }
        ZonedDateTime desde = a.atZoneSameInstant(ZONA);
        ZonedDateTime hasta = b.atZoneSameInstant(ZONA);
        long total = 0;
        LocalDate dia = desde.toLocalDate();
        for (int i = 0; i < MAX_DIAS && !dia.isAfter(hasta.toLocalDate()); i++, dia = dia.plusDays(1)) {
            Tramo tramo = cal.tramo(dia);
            if (tramo == null) {
                continue;
            }
            ZonedDateTime inicio = dia.atTime(tramo.inicio()).atZone(ZONA);
            ZonedDateTime fin = dia.atTime(tramo.fin()).atZone(ZONA);
            ZonedDateTime x = desde.isAfter(inicio) ? desde : inicio;
            ZonedDateTime y = hasta.isBefore(fin) ? hasta : fin;
            if (y.isAfter(x)) {
                total += Duration.between(x, y).toMinutes();
            }
        }
        return total;
    }

    /** Para mostrar en pantalla. */
    public List<HorarioLaboral> horario() {
        return horarios.findAllByOrderByDiaHorarioAsc();
    }
}
