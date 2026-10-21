package com.mesaayuda.dto;

import java.time.OffsetDateTime;

/**
 * Plazos del SLA de un ticket, para mostrarlos en su detalle (vencimiento y
 * cuenta regresiva). Antes solo se veían en agregado, en el dashboard.
 */
public record TicketSlaResponse(
        String politica,
        OffsetDateTime respuestaVence,
        OffsetDateTime resolucionVence,
        OffsetDateTime respondidoEn,
        OffsetDateTime resueltoEn,
        boolean respuestaVencida,
        boolean resolucionVencida,
        OffsetDateTime pausadoDesde,
        /** La prioridad la eligió una persona: NEXA no la cambia. */
        boolean prioridadManual
) {}
