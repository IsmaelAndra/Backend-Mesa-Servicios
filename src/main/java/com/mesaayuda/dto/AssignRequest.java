package com.mesaayuda.dto;

import jakarta.validation.constraints.NotNull;

public record AssignRequest(
        Long agentId,
        Long teamId
) {}