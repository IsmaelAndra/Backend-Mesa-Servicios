package com.mesaayuda.dto;

import java.util.UUID;

public record QuickReplyResponse(
        UUID id,
        String title,
        String content,
        boolean shared,
        /** Si es de quien consulta: solo entonces (o siendo administrador) la puede cambiar. */
        boolean mine,
        boolean editable,
        String ownerName
) {}
