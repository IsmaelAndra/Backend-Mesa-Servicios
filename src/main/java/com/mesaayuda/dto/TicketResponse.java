package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record TicketResponse(
        Long idTicket,
        String titleTicket,
        String descriptionTicket,
        String user,
        String agent,
        String team,
        String area,
        String category,
        String type,
        String status,
        String priority,
        Integer priorityLevel,
        OffsetDateTime closingDateTicket,
        OffsetDateTime createdAtTicket,
        OffsetDateTime updatedAtTicket
) {}