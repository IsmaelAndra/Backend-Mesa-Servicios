package com.mesaayuda.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Horas hábiles del SLA (V49): lunes a viernes 08:00–17:00, hora de Ecuador (UTC-5). */
class CalendarioLaboralServiceTest {

    private static final ZoneOffset EC = ZoneOffset.ofHours(-5);

    private static CalendarioLaboralService.Calendario cal(Set<LocalDate> feriados, Set<String> anuales) {
        Map<Integer, CalendarioLaboralService.Tramo> dias = new HashMap<>();
        for (int d = 1; d <= 5; d++) {
            dias.put(d, new CalendarioLaboralService.Tramo(LocalTime.of(8, 0), LocalTime.of(17, 0)));
        }
        return new CalendarioLaboralService.Calendario(dias, feriados, anuales);
    }

    private static OffsetDateTime t(int y, int m, int d, int h, int min) {
        return OffsetDateTime.of(y, m, d, h, min, 0, 0, EC);
    }

    @Test
    void dentroDelMismoDia() {
        // Miércoles 18/11/2026 10:00 + 2 h = 12:00
        assertEquals(t(2026, 11, 18, 12, 0), CalendarioLaboralService.sumar(cal(Set.of(), Set.of()), t(2026, 11, 18, 10, 0), 120));
    }

    @Test
    void delViernesPasaAlLunes() {
        // Viernes 20/11 16:00 + 4 h: 1 h el viernes + 3 h el lunes 23/11 → 11:00
        assertEquals(t(2026, 11, 23, 11, 0), CalendarioLaboralService.sumar(cal(Set.of(), Set.of()), t(2026, 11, 20, 16, 0), 240));
    }

    @Test
    void fueraDeHorarioEmpiezaAlAbrir() {
        // Jueves 19/11 20:00 + 1 h → viernes 20/11 09:00
        assertEquals(t(2026, 11, 20, 9, 0), CalendarioLaboralService.sumar(cal(Set.of(), Set.of()), t(2026, 11, 19, 20, 0), 60));
    }

    @Test
    void saltaLosFeriados() {
        // Navidad anual (25/12, viernes en 2026): jueves 24/12 16:00 + 2 h → lunes 28/12 09:00
        assertEquals(t(2026, 12, 28, 9, 0),
                CalendarioLaboralService.sumar(cal(Set.of(), Set.of("12-25")), t(2026, 12, 24, 16, 0), 120));
        // Feriado de una sola fecha
        assertEquals(t(2027, 2, 10, 9, 0),
                CalendarioLaboralService.sumar(cal(Set.of(LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9)), Set.of()),
                        t(2027, 2, 5, 16, 0), 120));
    }

    @Test
    void minutosHabilesEntreDosMomentos() {
        // Viernes 16:00 → lunes 11:00 = 1 h + 3 h
        assertEquals(240, CalendarioLaboralService.entre(cal(Set.of(), Set.of()), t(2026, 11, 20, 16, 0), t(2026, 11, 23, 11, 0)));
        // Al revés: 0
        assertEquals(0, CalendarioLaboralService.entre(cal(Set.of(), Set.of()), t(2026, 11, 23, 11, 0), t(2026, 11, 20, 16, 0)));
    }

    @Test
    void sinDiasLaborablesCuentaVeinticuatroHoras() {
        CalendarioLaboralService.Calendario vacio = new CalendarioLaboralService.Calendario(Map.of(), Set.of(), Set.of());
        assertEquals(t(2026, 11, 21, 10, 0), CalendarioLaboralService.sumar(vacio, t(2026, 11, 20, 10, 0), 24 * 60));
    }

    @Test
    void unPlazoEnDiasCuentaJornadasNoHorasDeOficina() {
        CalendarioLaboralService.Calendario c = cal(Set.of(), Set.of());
        // Jueves 19/11 14:29 + "2 d" = lunes 23/11 14:29 (dos jornadas), no el viernes siguiente.
        long habiles = CalendarioLaboralService.aMinutosHabiles(c, 2 * 1440);
        assertEquals(t(2026, 11, 23, 14, 29), CalendarioLaboralService.sumar(c, t(2026, 11, 19, 14, 29), habiles));
        // "8 h" sigue siendo ocho horas de oficina.
        assertEquals(480, CalendarioLaboralService.aMinutosHabiles(c, 480));
        // "1 d 2 h" = una jornada + 2 h.
        assertEquals(9 * 60 + 120, CalendarioLaboralService.aMinutosHabiles(c, 1440 + 120));
    }
}
