package com.mesaayuda.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetResponse(
        UUID idAsset,
        String codeAsset,
        String nameAsset,
        String typeAsset,
        String brandAsset,
        String modelAsset,
        String serialNumberAsset,
        String statusAsset,
        OffsetDateTime purchaseDateAsset,
        String providerAsset,
        BigDecimal costAsset,
        OffsetDateTime warrantyUntilAsset,
        String imageUrlAsset,
        String documentUrlAsset,
        String notesAsset,
        UUID assignedUserId,
        String assignedUser,
        String assignedUserDisplayName,
        /** Foto de la persona asignada, para mostrarla junto a su nombre. */
        String assignedUserPhotoUrl,
        /** Área de la persona asignada (puede no coincidir con la del activo). */
        String assignedUserArea,
        UUID areaId,
        String area,
        UUID sucursalId,
        String sucursal,
        OffsetDateTime createdAtAsset,
        OffsetDateTime updatedAtAsset
) {
}