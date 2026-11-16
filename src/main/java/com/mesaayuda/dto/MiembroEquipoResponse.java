package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persona de un equipo (o candidata a serlo). Vista compacta: antes
 * GET /admin/teams/{id}/members devolvía la entidad completa del usuario.
 */
public record MiembroEquipoResponse(
        UUID idUser,
        String nombre,
        String correo,
        String rol,
        String area,
        OffsetDateTime ultimaAsignacion) {
}
