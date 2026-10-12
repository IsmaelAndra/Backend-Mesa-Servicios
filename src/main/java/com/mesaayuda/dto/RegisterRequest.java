package com.mesaayuda.dto;

import jakarta.validation.constraints.Email;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import jakarta.validation.constraints.Size;

import java.util.UUID;
public record RegisterRequest(
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
        UUID areaId,

        /**
         * Sede fisica de la persona. Opcional: las cuentas que ya existen y las
         * que se den de alta sin sede siguen siendo validas, y obligarla
         * impediria dar de alta a alguien antes de tener el catalogo cargado.
         */
        UUID sucursalId
) {}