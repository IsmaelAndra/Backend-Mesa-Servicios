package com.mesaayuda.dto;

import java.time.OffsetDateTime;

public record UserResponse(
        Long idUser,
        String identificationUser,
        String corporateEmailUser,
        String phoneUser,
        String statusUser,
        String role,
        String area,
        OffsetDateTime createdAtUser
) {}