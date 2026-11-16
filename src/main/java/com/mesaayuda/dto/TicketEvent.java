package com.mesaayuda.dto;

import java.time.Instant;

import java.util.UUID;
public record TicketEvent(
        Long seq,
        String type,
        UUID ticketId,
        /** Número legible del ticket, para mostrarlo ("#43"). */
        Long ticketNumber,
        String ticketTitle,
        String status,
        UUID actorId,
        String actorName,
        UUID assignedAgentId,
        String assignedAgentName,
        /**
         * Titular del ticket. No se muestra al usuario: se usa solo para decidir
         * a quien se le envía el evento. Sin él, el canal no puede distinguir los
         * tickets propios de los ajenos y tendría que emitirlos todos.
         */
        UUID requesterId,
        Instant timestamp,
        /** Área que atiende y área del solicitante: solo para decidir a quién se envía. */
        UUID areaDestinoId,
        UUID areaSolicitanteId,
        /** Confidencial (y si está abierto al área): cambia a quién se envía. */
        boolean confidencial,
        boolean confidencialArea) {

    /**
     * Copia sin el titular, para enviar al cliente.
     *
     * <p>El navegador no necesita el id del solicitante y no tiene por qué
     * conocerlo: bastan el titulo y el estado, que son los que usa la lista para
     * actualizarse. Se quita aqui para no exponer un dato de otro usuario en un
     * canal que se entrega sin filtrar por recurso.
     */
    public TicketEvent paraCliente() {
        return new TicketEvent(seq, type, ticketId, ticketNumber, ticketTitle, status, actorId, actorName,
                assignedAgentId, assignedAgentName, null, timestamp, null, null, confidencial, false);
    }
}