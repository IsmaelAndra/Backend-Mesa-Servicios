package com.mesaayuda.dto;

import java.util.UUID;

/**
 * Persona a la que se puede asignar un activo, con lo justo para el
 * desplegable: nombre, foto, área y punto de presencia. Sin identificación ni
 * teléfono (mismo criterio que {@link AgentOptionResponse}).
 */
public record AssetAssignableUserResponse(
        UUID idUser,
        String displayName,
        String corporateEmailUser,
        String photoUrl,
        String area,
        String sessionState
) {
}
