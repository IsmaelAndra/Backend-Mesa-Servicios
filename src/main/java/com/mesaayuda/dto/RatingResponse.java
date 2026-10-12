package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
public record RatingResponse(
        UUID idRating,
        UUID ticketId,
        Integer requirementsMet,
        Boolean allRequirementsMet,
        Integer responseTimeRating,
        Integer resolutionQuality,
        Integer agentCommunication,
        Integer overallSatisfaction,
        String comments,
        OffsetDateTime ratedAt
) {}