package com.mesaayuda.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "La identificación es obligatoria")
        @Size(min = 6, max = 30, message = "La identificación debe tener entre 6 y 30 caracteres")
        String identification,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Formato de correo inválido")
        String corporateEmail,

        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        String phone,

        @NotBlank(message = "El rol es obligatorio")
        String roleName,

        @NotNull(message = "El área es obligatoria")
        Long areaId
) {}