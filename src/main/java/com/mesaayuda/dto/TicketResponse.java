package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record TicketResponse(
        UUID idTicket,
        UUID userId,
        String titleTicket,
        String descriptionTicket,
        String user,
        String userDisplayName,
        String userPhotoUrl,
        String userStatus,
        String userSessionState,
        String agent,
        String team,
        String area,
        UUID sucursalId,
        String sucursal,
        String category,
        String type,
        String status,
        String priority,
        Integer priorityLevel,
        OffsetDateTime closingDateTicket,
        OffsetDateTime createdAtTicket,
        OffsetDateTime updatedAtTicket,
        /** Número legible del ticket ("#43"); el id es interno. */
        Long numberTicket
) {

    /**
     * Copia con el estado de sesión del solicitante. userStatus es el estado
     * manual de la cuenta; este campo es si la persona está conectada ahora.
     */
    public TicketResponse withUserSessionState(String sessionState) {
        if (sessionState == null || sessionState.equals(this.userSessionState)) {
            return this;
        }
        return new TicketResponse(idTicket, userId, titleTicket, descriptionTicket, user,
userDisplayName, userPhotoUrl, userStatus, sessionState, agent, team, area,
      sucursalId, sucursal,
      category, type, status, priority, priorityLevel, closingDateTicket,
      createdAtTicket, updatedAtTicket, numberTicket);
    }
}