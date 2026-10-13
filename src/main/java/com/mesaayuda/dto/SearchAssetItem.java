package com.mesaayuda.dto;

import java.util.UUID;

/**
 * Ítem compacto de activo para la búsqueda global.
 *
 * @param idAsset            UUID del activo
 * @param codeAsset          código legible del activo (p.ej. LAP-001)
 * @param nameAsset          nombre del activo
 * @param typeAsset          tipo (LAPTOP, MONITOR, DESKTOP, etc.)
 * @param statusAsset        estado (DISPONIBLE, ASIGNADO, EN_REPARACION, BAJA)
 * @param assignedUser       correo del usuario asignado (si lo hay)
 * @param assignedUserName   nombre completo del usuario asignado (si lo hay)
 * @param area               nombre del área a la que pertenece
 */
public record SearchAssetItem(
        UUID idAsset,
        String codeAsset,
        String nameAsset,
        String typeAsset,
        String statusAsset,
        String assignedUser,
        String assignedUserName,
        String area
) {
}