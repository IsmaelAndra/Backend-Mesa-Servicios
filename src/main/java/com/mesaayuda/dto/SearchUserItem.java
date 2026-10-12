package com.mesaayuda.dto;
import java.util.UUID;

/**
 * Resultado compacto de una persona en la búsqueda global.
 *
 * <p>Reutiliza exactamente el mismo criterio de mínimo espejo que ya aplica
 * {@link AgentOptionResponse} al listado de agentes: nunca expone aquí la
 * identificación, el teléfono, el UPN ni los flags de sincronización de
 * Microsoft 365. Esos datos viven en {@code GET /users/{id}}, que exige
 * administrador y no se toca. La búsqueda solo responde lo que hace falta para
 * distinguir a la persona y abrir su ficha.
 *
 * @param idUser     identificador, para abrir la ficha del usuario
 * @param displayName nombre mostrado (o correo si no hay nombre, igual que
 *                   {@code User.getFullName()})
 * @param corporateEmailUser correo corporativo
 * @param role       rol: USER o AGENT
 * @param area       nombre del área a la que pertenece la persona
 * @param photoUrl   avatar, si lo tiene
 */
public record SearchUserItem(UUID idUser, String displayName, String corporateEmailUser,
        String role, String area, String photoUrl) {
}