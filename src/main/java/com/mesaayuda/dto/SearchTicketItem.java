package com.mesaayuda.dto;

import java.time.OffsetDateTime;

import java.util.UUID;
/**
 * Resultado compacto de un ticket en la búsqueda global.
 *
 * <p>Es una vista de salida de {@code GET /search}, no el {@link TicketResponse}
 * completo: la búsqueda devuelve grupos de hasta cinco elementos y el frontend
 * solo necesita localizar el ticket para abrirlo. El detalle y los permisos se
 * resuelven después en {@code GET /tickets/{id}}, que ya aplica
 * {@code TicketAccessPolicy}.
 *
 * @param idTicket       para abrir el ticket desde el resultado
 * @param titleTicket    título con el que se busca
 * @param status         estado actual (NUEVO, EN_PROCESO, CERRADO...)
 * @param priority       prioridad (ALTA, MEDIA, BAJA)
 * @param createdAtTicket fecha de creación, para ordenar la lista
 */
public record SearchTicketItem(UUID idTicket, String titleTicket, String status,
        String priority, OffsetDateTime createdAtTicket, Long numberTicket) {
}