package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Solicitud de acceso de emergencia a un confidencial. */
public record AccesoConfidencialResponse(UUID id, UUID ticketId, Long ticketNumber, String solicitante,
                                         String motivo, String estado, String comentario,
                                         OffsetDateTime creada, OffsetDateTime vence) {
}
