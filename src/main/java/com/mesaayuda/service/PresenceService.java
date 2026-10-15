package com.mesaayuda.service;
import java.util.UUID;

import com.mesaayuda.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Estado de sesión de los usuarios, para distinguirlos de las cuentas habilitadas.
 *
 * - EN_LINEA: tiene una conexión SSE abierta ahora mismo (el registro está en
 *   SseEventBroker, que desde que el navbar abre el SSE es toda la app).
 * - RECIENTE: no hay conexión viva, pero renovó su token hace menos del umbral.
 *   El token de acceso dura 15 minutos, así que un usuario que estuvo trabajando
 *   deja de renovar en ese plazo aunque no cerrara sesión.
 * - DESCONECTADO: nada de lo anterior.
 */
@Service
public class PresenceService {

    public static final String EN_LINEA = "EN_LINEA";
    public static final String RECIENTE = "RECIENTE";
    public static final String DESCONECTADO = "DESCONECTADO";

    @Autowired
    private SseEventBroker sseEventBroker;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /** Minutos de gracia para marcar RECIENTE. Igual a la vida del token de acceso. */
    @Value("${app.jwt.expiration-ms:900000}")
    private long accessTokenMs = 900_000L;

    @Transactional(readOnly = true)
    public String estadoDe(UUID userId) {
        if (userId == null) {
            return DESCONECTADO;
        }
        return estadosDe(List.of(userId)).getOrDefault(userId, DESCONECTADO);
    }

    /**
     * Estado de varios usuarios en una sola consulta. Los ids nulos se ignoran.
     * Un usuario sin conexión SSE pero con actividad reciente sale como RECIENTE.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> estadosDe(Collection<UUID> userIds) {
        Map<UUID, String> resultado = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return resultado;
        }

        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        for (UUID id : ids) {
            resultado.put(id, sseEventBroker.isConectado(id) ? EN_LINEA : DESCONECTADO);
        }
        resultado.putAll(recientesEntre(ids));
        return resultado;
    }

    /** Ids sin conexión SSE cuya última renovación de token cae dentro del umbral. */
    private Map<UUID, String> recientesEntre(List<UUID> ids) {
        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime limite = ahora.minus(Duration.ofMillis(Math.max(60_000L, accessTokenMs)));

        List<UUID> sinConexion = ids.stream()
                .filter(id -> DESCONECTADO.equals(estadoPropio(id)))
                .toList();
        if (sinConexion.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> salida = new HashMap<>();
        // Cerró la aplicación hace poco: es lo que de verdad significa "Reciente".
        // La renovación del token (abajo) solo ocurre cada 15 minutos, así que por
        // sí sola casi nunca marcaba a nadie como reciente.
        for (UUID id : sinConexion) {
            Instant salio = sseEventBroker.ultimaDesconexion(id);
            if (salio != null && !OffsetDateTime.ofInstant(salio, ZoneOffset.UTC).isBefore(limite)) {
                salida.put(id, RECIENTE);
            }
        }
        for (Object[] fila : refreshTokenRepository.ultimaActividadPorUsuario(sinConexion, ahora)) {
            if (!(fila[0] instanceof UUID id)) {
                continue;
            }
            OffsetDateTime momento = aOffsetDateTime(fila[1]);
            if (momento != null && !momento.isBefore(limite)) {
                salida.put(id, RECIENTE);
            }
        }
        return salida;
    }

    private String estadoPropio(UUID userId) {
        return sseEventBroker.isConectado(userId) ? EN_LINEA : DESCONECTADO;
    }

    private OffsetDateTime aOffsetDateTime(Object valor) {
        if (valor instanceof OffsetDateTime odt) {
            return odt;
        }
        if (valor instanceof Instant inst) {
            return OffsetDateTime.ofInstant(inst, ZoneOffset.UTC);
        }
        return null;
    }
}
