package com.mesaayuda.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetRequest(
        @NotBlank @Size(max = 50) String codeAsset,
        @NotBlank @Size(max = 150) String nameAsset,
        @NotBlank @Size(max = 50) String typeAsset,
        @Size(max = 100) String brandAsset,
        @Size(max = 100) String modelAsset,
        @Size(max = 100) String serialNumberAsset,
        @Size(max = 30) String statusAsset,
        OffsetDateTime purchaseDateAsset,
        @Size(max = 150) String providerAsset,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costAsset,
        OffsetDateTime warrantyUntilAsset,
        @Size(max = 500) String imageUrlAsset,
        @Size(max = 500) String documentUrlAsset,
        String notesAsset,
        UUID assignedUserId,
        UUID areaId,
        UUID sucursalId
) {
}