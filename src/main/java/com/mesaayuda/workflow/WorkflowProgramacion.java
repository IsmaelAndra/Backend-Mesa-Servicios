package com.mesaayuda.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.exception.BadRequestException;
import org.springframework.scheduling.support.CronExpression;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Cuándo toca la siguiente ejecución de un disparador programado.
 *
 * <p>La configuración del disparador {@code SCHEDULED} admite tres formas:
 * <pre>
 *   {"modo":"INTERVALO","minutos":60}                  cada N minutos (mínimo 5)
 *   {"modo":"CRON","cron":"0 0 8 * * MON-FRI"}         expresión cron de Spring (6 campos)
 *   {"modo":"FECHA","fecha":"2026-10-10T08:00:00-05:00"}  una sola vez
 * </pre>
 * Las horas del cron se interpretan en la zona de la cooperativa
 * (America/Guayaquil), la misma que usan el resto de tareas programadas.
 */
public final class WorkflowProgramacion {

    public static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    /** Por debajo de esto un workflow programado es más carga que utilidad. */
    public static final int MINUTOS_MINIMOS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowProgramacion() {
    }

    /** Comprueba que la configuración sea válida; lanza 400 con el motivo si no. */
    public static void validar(String configJson) {
        calcular(configJson, OffsetDateTime.now(), true);
    }

    /**
     * Siguiente ejecución estrictamente posterior a {@code desde}, o null si ya no
     * hay más (una FECHA que pasó).
     */
    public static OffsetDateTime siguiente(String configJson, OffsetDateTime desde) {
        return calcular(configJson, desde, false);
    }

    private static OffsetDateTime calcular(String configJson, OffsetDateTime desde, boolean validando) {
        JsonNode config;
        try {
            config = MAPPER.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
        } catch (Exception e) {
            throw new BadRequestException("La programación del workflow no es un JSON válido.");
        }
        String modo = config.path("modo").asText("").trim().toUpperCase(Locale.ROOT);
        switch (modo) {
            case "INTERVALO" -> {
                int minutos = config.path("minutos").asInt(0);
                if (minutos < MINUTOS_MINIMOS) {
                    throw new BadRequestException("El intervalo debe ser de al menos " + MINUTOS_MINIMOS
                            + " minutos.");
                }
                return desde.plusMinutes(minutos);
            }
            case "CRON" -> {
                String expresion = config.path("cron").asText("").trim();
                CronExpression cron;
                try {
                    cron = CronExpression.parse(expresion);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("La expresión cron '" + expresion + "' no es válida. "
                            + "Use 6 campos: segundo minuto hora día mes día-semana (p. ej. 0 0 8 * * MON-FRI).");
                }
                ZonedDateTime proxima = cron.next(desde.atZoneSameInstant(ZONA));
                if (proxima == null) {
                    throw new BadRequestException("La expresión cron no vuelve a cumplirse nunca.");
                }
                OffsetDateTime siguiente = proxima.toOffsetDateTime();
                // Mismo suelo que el intervalo: un cron de cada minuto inunda la cola.
                ZonedDateTime despues = cron.next(proxima);
                if (despues != null && despues.toOffsetDateTime().isBefore(siguiente.plusMinutes(MINUTOS_MINIMOS))) {
                    throw new BadRequestException("El cron se repite con demasiada frecuencia: el mínimo es cada "
                            + MINUTOS_MINIMOS + " minutos.");
                }
                return siguiente;
            }
            case "FECHA" -> {
                OffsetDateTime fecha;
                try {
                    fecha = OffsetDateTime.parse(config.path("fecha").asText(""));
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("La fecha de la programación no es válida "
                            + "(formato ISO, p. ej. 2026-10-10T08:00:00-05:00).");
                }
                if (fecha.isAfter(desde)) {
                    return fecha;
                }
                if (validando) {
                    throw new BadRequestException("La fecha de la programación ya pasó.");
                }
                return null;
            }
            default -> throw new BadRequestException(
                    "La programación necesita un modo: INTERVALO, CRON o FECHA.");
        }
    }
}
