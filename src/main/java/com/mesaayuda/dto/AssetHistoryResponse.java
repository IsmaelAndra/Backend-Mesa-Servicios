package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento del historial de un activo.
 *
 * @param user / userDisplayName / userPhotoUrl  persona a la que afecta
 *        (nuevo responsable, quien lo devuelve...). Null si no hay.
 * @param previousUser...                         en una reasignación, quién lo tenía.
 * @param performedBy...                          quién hizo el cambio.
 */
public record AssetHistoryResponse(
        UUID idAssetHistory,
        UUID assetId,
        String assetCode,
        String assetName,
        UUID userId,
        String user,
        String userDisplayName,
        String userPhotoUrl,
        UUID previousUserId,
        String previousUser,
        String previousUserDisplayName,
        UUID performedById,
        String performedBy,
        String performedByDisplayName,
        String actionType,
        String notes,
        OffsetDateTime createdAtAssetHistory
) {
}
