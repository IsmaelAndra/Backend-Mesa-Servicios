package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Estado de la cola de correo para el panel del SUPER_ADMIN. */
public record MailOutboxResumenResponse(
        boolean envioHabilitado,
        String remitente,
        int limitePorMinuto,
        long enviadosUltimoMinuto,
        /** Si Microsoft pidió esperar (HTTP 429/503), hasta cuándo está en pausa el envío. */
        OffsetDateTime pausadoHasta,
        long pendientes,
        long enviando,
        long fallidos,
        long enviadosHoy,
        long agrupadosHoy,
        long fallidosHoy,
        /** Antigüedad del pendiente más viejo, en segundos (null si no hay). */
        Long segundosPendienteMasAntiguo,
        /** Minutos que tardaría en vaciarse la cola al ritmo del límite. */
        long minutosParaVaciar,
        List<PuntoHora> porHora,
        List<TipoCantidad> porTipo
) {
    public record PuntoHora(OffsetDateTime hora, long enviados, long agrupados, long fallidos, long pendientes) {}

    public record TipoCantidad(String tipo, long total) {}
}
