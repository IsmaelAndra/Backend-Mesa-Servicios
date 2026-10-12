package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Fila de auditoría del sistema, tal como se ve en la pantalla de consulta.
 *
 * <p>Es un tipo aparte del {@code AuditLogResponse} de ticket a propósito. Aquel
 * es el historial de cambios de un ticket (valor anterior y nuevo de un campo);
 * este es el rastro de eventos de seguridad y administración: quién hizo qué,
 * desde dónde y en qué petición. Son preguntas distintas y mezclarlas en la misma
 * pantalla haría que una sirviera para poco.
 *
 * @param id        identificador de la fila
 * @param usuario   quién lo hizo; "sistema" si no hay sesión detrás
 * @param accion    tipo de evento: LOGIN, MFA_ENROLLED, PASSWORD_RESET_REQUESTED…
 * @param entidad   sobre qué se actuó: User, MfaSetting…
 * @param entidadId sobre qué registro concreto
 * @param ip        de dónde vino la petición, ya resuelta por ClientIpFilter
 * @param detalles  texto libre con el contexto; nunca contiene cuerpos de petición
 * @param metodo    verbo HTTP, para poder reconstruir la petición
 * @param ruta      ruta chamada, útil para agrupar por pantalla
 * @param requestId identificador que también está en el log del servidor, para
 *                  unir una fila de la tabla con su línea de log
 * @param fecha     cuándo ocurrió
 */
public record SystemAuditLogResponse(
        UUID id,
        String usuario,
        String accion,
        String entidad,
        UUID entidadId,
        String ip,
        String detalles,
        String metodo,
        String ruta,
        String requestId,
        OffsetDateTime fecha
) {
}