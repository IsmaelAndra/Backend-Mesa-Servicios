package com.mesaayuda.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;
public record AssignRequest(
        UUID agentId,
        UUID teamId
) {}