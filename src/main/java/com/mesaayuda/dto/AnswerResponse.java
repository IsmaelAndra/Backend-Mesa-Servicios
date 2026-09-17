package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record AnswerResponse(
        Long idAnswer,
        String messageAnswer,
        Boolean internalAnswer,
        String user,
        OffsetDateTime createdAtAnswer
) {}