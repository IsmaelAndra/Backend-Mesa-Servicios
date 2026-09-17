package com.mesaayuda.dto;

import java.util.List;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String email,
        String role,
        String area,
        List<String> permissions,
        Boolean mustChangePassword,
        String refreshToken
) {}