package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un dispositivo con sesión abierta (Mi cuenta → Dispositivos).
 *
 * @param id          id de la sesión (no del token), para cerrarla
 * @param dispositivo "Chrome en Windows"...
 * @param ip          IP de la última renovación
 * @param inicio      cuándo se inició sesión en ese dispositivo
 * @param ultimoUso   última renovación de la sesión
 * @param actual      es el dispositivo desde el que se consulta
 */
public record SesionDispositivoResponse(UUID id, String dispositivo, String ip,
        OffsetDateTime inicio, OffsetDateTime ultimoUso, boolean actual) {
}
