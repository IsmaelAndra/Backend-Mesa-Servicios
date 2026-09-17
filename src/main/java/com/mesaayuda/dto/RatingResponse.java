package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record RatingResponse(
        Long idRating,
        Long ticketId,
        Integer requirementsMet,
        Boolean allRequirementsMet,
        Integer responseTimeRating,
        Integer resolutionQuality,
        Integer agentCommunication,
        Integer overallSatisfaction,
        String comments,
        OffsetDateTime ratedAt
) {}