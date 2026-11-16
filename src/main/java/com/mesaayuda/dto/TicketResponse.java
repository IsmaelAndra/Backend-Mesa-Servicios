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
        Long numberTicket,
        /** Área que atiende el ticket (V28); "area" es la del solicitante. */
        UUID areaDestinoId,
        String areaDestino,
        /**
         * Lo que la persona que pide el detalle puede hacer con ESTE ticket
         * (null en listas). Así la pantalla no ofrece botones que darían 403,
         * p. ej. al jefe que solo hace seguimiento de un ticket de su gente.
         */
        Boolean puedeGestionar,
        Boolean puedeComentar,
        /** Ticket confidencial (V29): solo lo ven las personas autorizadas. */
        Boolean confidencial
) {

    /** Texto que sustituye al título de un confidencial para quien no puede verlo. */
    public static final String TITULO_CONFIDENCIAL = "Ticket confidencial";

    /** Compatibilidad: sin permisos ni confidencialidad. */
    public TicketResponse(UUID idTicket, UUID userId, String titleTicket, String descriptionTicket, String user,
                          String userDisplayName, String userPhotoUrl, String userStatus, String userSessionState,
                          String agent, String team, String area, UUID sucursalId, String sucursal,
                          String category, String type, String status, String priority, Integer priorityLevel,
                          OffsetDateTime closingDateTicket, OffsetDateTime createdAtTicket,
                          OffsetDateTime updatedAtTicket, Long numberTicket, UUID areaDestinoId, String areaDestino) {
        this(idTicket, userId, titleTicket, descriptionTicket, user, userDisplayName, userPhotoUrl, userStatus,
                userSessionState, agent, team, area, sucursalId, sucursal, category, type, status, priority,
                priorityLevel, closingDateTicket, createdAtTicket, updatedAtTicket, numberTicket, areaDestinoId,
                areaDestino, null, null, null);
    }

    /** Compatibilidad: sin área que atiende. */
    public TicketResponse(UUID idTicket, UUID userId, String titleTicket, String descriptionTicket, String user,
                          String userDisplayName, String userPhotoUrl, String userStatus, String userSessionState,
                          String agent, String team, String area, UUID sucursalId, String sucursal,
                          String category, String type, String status, String priority, Integer priorityLevel,
                          OffsetDateTime closingDateTicket, OffsetDateTime createdAtTicket,
                          OffsetDateTime updatedAtTicket, Long numberTicket) {
        this(idTicket, userId, titleTicket, descriptionTicket, user, userDisplayName, userPhotoUrl, userStatus,
                userSessionState, agent, team, area, sucursalId, sucursal, category, type, status, priority,
                priorityLevel, closingDateTicket, createdAtTicket, updatedAtTicket, numberTicket, null, null);
    }

    /** Copia con los permisos de quien abre el detalle. */
    public TicketResponse conPermisos(boolean gestionar, boolean comentar) {
        return new TicketResponse(idTicket, userId, titleTicket, descriptionTicket, user, userDisplayName,
                userPhotoUrl, userStatus, userSessionState, agent, team, area, sucursalId, sucursal, category, type,
                status, priority, priorityLevel, closingDateTicket, createdAtTicket, updatedAtTicket, numberTicket,
                areaDestinoId, areaDestino, gestionar, comentar, confidencial);
    }

    /**
     * Copia para quien NO puede ver un confidencial (súper admin o auditor sin
     * acceso aprobado): sabe que existe, su número, estado y área, pero no el
     * título, el contenido ni quién lo pidió. Con eso puede solicitar acceso.
     */
    public TicketResponse oculto() {
        return new TicketResponse(idTicket, null, TITULO_CONFIDENCIAL, null, null, "Confidencial", null, null,
                null, null, team, null, null, null, null, null, status, priority, priorityLevel, closingDateTicket,
                createdAtTicket, updatedAtTicket, numberTicket, areaDestinoId, areaDestino, false, false, true);
    }

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
                sucursalId, sucursal, category, type, status, priority, priorityLevel, closingDateTicket,
                createdAtTicket, updatedAtTicket, numberTicket, areaDestinoId, areaDestino, puedeGestionar,
                puedeComentar, confidencial);
    }
}
