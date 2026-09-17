package com.mesaayuda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SupervisorEvaluationRequest(
        @Min(value = 1, message = "Debe ser entre 1 y 5")
        @Max(value = 5, message = "Debe ser entre 1 y 5")
        Integer supervisorRating,

        String supervisorComments,

        String status
) {}