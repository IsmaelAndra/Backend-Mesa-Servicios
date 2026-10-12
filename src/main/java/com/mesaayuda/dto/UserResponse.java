package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record UserResponse(
        UUID idUser,
        String identificationUser,
        String corporateEmailUser,
        String phoneUser,
        String statusUser,
        String sessionState,
        String role,
        String area,
        UUID sucursalId,
        String sucursal,
        String photoUrl,
        String displayName,
        String firstName,
        String lastName,
        String userPrincipalName,
        Boolean microsoftActive,
        Boolean microsoftSynced,
        OffsetDateTime lastSync,
        OffsetDateTime createdAtUser
) {

    /**
     * Copia con el estado de sesión del usuario. statusUser es el estado manual
     * de la cuenta; sessionState dice si la persona está conectada ahora mismo.
     */
    public UserResponse withSessionState(String sessionState) {
        if (sessionState == null || sessionState.equals(this.sessionState)) {
            return this;
        }
        return new UserResponse(idUser, identificationUser, corporateEmailUser, phoneUser,
                statusUser, sessionState, role, area, sucursalId, sucursal, photoUrl, displayName, firstName, lastName,
                userPrincipalName, microsoftActive, microsoftSynced, lastSync, createdAtUser);
    }
}