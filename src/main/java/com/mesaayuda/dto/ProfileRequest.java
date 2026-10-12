package com.mesaayuda.dto;

import jakarta.validation.constraints.Size;

/**
 * Cambio de perfil del propio usuario.
 *
 * Antes el perfil se guardaba contra PUT /users/{id}, que espera un
 * RegisterRequest: ese DTO exige roleName y areaId, y el formulario de perfil
 * no manda esos campos, asi que el backend respondia 400 Bad Request siempre.
 * Ademas esa ruta es de administracion, de modo que un usuario normal no
 * podia editar ni siquiera su propia ficha.
 *
 * Aqui solo van los campos que la persona controla sobre si misma. El rol, el
 * area, el estado y el correo corporativo los cambia un administrador, no el
 * usuario.
 */
public record ProfileRequest(
        @Size(min = 6, max = 30, message = "La identificación debe tener entre 6 y 30 caracteres")
        String identificationUser,

        String phoneUser,

        String firstName,

        String lastName,

        String displayName
) {
}
