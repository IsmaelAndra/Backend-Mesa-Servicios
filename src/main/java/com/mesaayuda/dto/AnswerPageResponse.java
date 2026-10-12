package com.mesaayuda.dto;

import java.util.List;

/**
 * Página del hilo de un ticket.
 * <p>
 * Antes el endpoint devolvía la lista completa y era el navegador quien
 * recortaba 15 en 15 con un paginador. Eso se veía mal en el chat y, con hilos
 * largos, obligaba a descargar cientos de mensajes para mostrar los últimos
 * quince. Ahora el servidor pagina: la pantalla pide la última página y, al
 * llegar arriba, pide la anterior.
 */
public record AnswerPageResponse(
        /** Mensajes de esta página, ya en orden de más antiguo a más reciente. */
        List<AnswerResponse> answers,
        int page,
        int size,
        /** Si hay mensajes más antiguos que esta página. */
        boolean hasMore,
        /** Total de mensajes visibles para quien consulta. */
        long total
) {}
