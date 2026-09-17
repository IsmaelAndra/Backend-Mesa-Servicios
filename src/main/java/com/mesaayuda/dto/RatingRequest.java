package com.mesaayuda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RatingRequest(
        @NotNull(message = "Requerimientos cumplidos es obligatorio")
        @Min(value = 1, message = "Debe ser entre 1 y 5")
        @Max(value = 5, message = "Debe ser entre 1 y 5")
        Integer requirementsMet,

        Boolean allRequirementsMet,

        @Min(value = 1, message = "Debe ser entre 1 y 5")
        @Max(value = 5, message = "Debe ser entre 1 y 5")
        Integer responseTimeRating,

        @Min(value = 1, message = "Debe ser entre 1 y 5")
        @Max(value = 5, message = "Debe ser entre 1 y 5")
        Integer resolutionQuality,

        @Min(value = 1, message = "Debe ser entre 1 y 5")
        @Max(value = 5, message = "Debe ser entre 1 y 5")
        Integer agentCommunication,

        @Min(value = 1, message = "Debe ser entre 1 y 5")
        @Max(value = 5, message = "Debe ser entre 1 y 5")
        Integer overallSatisfaction,

        String comments
) {}