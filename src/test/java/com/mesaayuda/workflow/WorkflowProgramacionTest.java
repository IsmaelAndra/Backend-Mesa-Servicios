package com.mesaayuda.workflow;

import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Cálculo de la siguiente ejecución de un disparador programado. */
class WorkflowProgramacionTest {

    private static final OffsetDateTime LUNES_7AM_ECUADOR =
            OffsetDateTime.of(2026, 10, 5, 7, 0, 0, 0, ZoneOffset.ofHours(-5));

    @Test
    void intervaloSumaLosMinutos() {
        assertEquals(LUNES_7AM_ECUADOR.plusMinutes(60),
                WorkflowProgramacion.siguiente("{\"modo\":\"INTERVALO\",\"minutos\":60}", LUNES_7AM_ECUADOR));
    }

    @Test
    void cronSeInterpretaEnHoraDeEcuador() {
        // "A las 8" es a las 8 de Guayaquil, aunque el servidor esté en UTC.
        OffsetDateTime siguiente = WorkflowProgramacion.siguiente(
                "{\"modo\":\"CRON\",\"cron\":\"0 0 8 * * MON-FRI\"}",
                LUNES_7AM_ECUADOR.withOffsetSameInstant(ZoneOffset.UTC));
        assertEquals(LUNES_7AM_ECUADOR.plusHours(1).toInstant(), siguiente.toInstant());
    }

    @Test
    void fechaPasadaYaNoSeRepite() {
        String config = "{\"modo\":\"FECHA\",\"fecha\":\"2026-10-05T06:00:00-05:00\"}";
        assertNull(WorkflowProgramacion.siguiente(config, LUNES_7AM_ECUADOR));
    }

    @Test
    void frecuenciasDemasiadoAltasSeRechazan() {
        assertThrows(BadRequestException.class,
                () -> WorkflowProgramacion.validar("{\"modo\":\"INTERVALO\",\"minutos\":2}"));
        assertThrows(BadRequestException.class,
                () -> WorkflowProgramacion.validar("{\"modo\":\"CRON\",\"cron\":\"0 * * * * *\"}"));
    }

    @Test
    void cronMalEscritoSeExplica() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> WorkflowProgramacion.validar("{\"modo\":\"CRON\",\"cron\":\"todos los lunes\"}"));
        assertEquals(true, e.getMessage().contains("6 campos"), e.getMessage());
    }
}
