package com.mesaayuda.dto;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;
import java.util.UUID;
import java.time.OffsetDateTime;

import java.util.UUID;
public record MonthlyEvaluationResponse(
        UUID idEvaluation,
        UUID agentId,
        String agentEmail,
        /** Nombre de la persona, resuelto con la misma prioridad que en el chat. */
        String agentDisplayName,
        /** Foto de perfil, o cadena vacía si el agente no tiene ninguna. */
        String agentPhotoUrl,
        /** Estado de sesión del agente, para el punto de presencia de la tabla. */
        String agentSessionState,
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