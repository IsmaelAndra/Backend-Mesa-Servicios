package com.mesaayuda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MonthlyEvaluationResponse(
        Long idEvaluation,
        Long agentId,
        String agentEmail,
        LocalDate evaluationMonth,
        Integer ticketsClosed,
        BigDecimal avgResponseTimeMinutes,
        BigDecimal avgResolutionTimeMinutes,
        BigDecimal slaComplianceRate,
        BigDecimal avgRequirementsMet,
        BigDecimal avgOverallSatisfaction,
        BigDecimal satisfactionScore,
        Integer supervisorRating,
        String supervisorComments,
        String status,
        OffsetDateTime createdAt
) {}