package com.mesaayuda.service;

import com.mesaayuda.dto.SesionDispositivoResponse;
import com.mesaayuda.entity.RefreshToken;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.RefreshTokenRepository;
import com.mesaayuda.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Dispositivos con sesión abierta", como en las apps de mensajería: cada
 * persona ve dónde tiene abierta su cuenta y puede cerrar cualquiera de esas
 * sesiones (o todas menos la actual).
 *
 * <p>Cerrar una sesión revoca su refresh token y además corta los tokens de
 * acceso de la persona (como el logout): el dispositivo cerrado queda fuera al
 * instante y los demás no lo notan, porque su interceptor renueva solo.
 */
@Service
@RequiredArgsConstructor
public class SesionDispositivoService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthService authService;
    private final SystemAuditService systemAuditService;
    private final SseEventBroker sseEventBroker;

    @Transactional(readOnly = true)
    public List<SesionDispositivoResponse> misSesiones(String refreshActual) {
        User yo = userService.getCurrentUser();
        UUID actual = authService.sesionDe(refreshActual);
        // Un token vivo por sesión; si hubiera dos (carrera al rotar), el más reciente.
        Map<UUID, RefreshToken> porSesion = new LinkedHashMap<>();
        for (RefreshToken rt : refreshTokenRepository.vivosDe(yo.getIdUser(), OffsetDateTime.now())) {
            UUID clave = rt.getSesion() != null ? rt.getSesion() : rt.getIdRefreshToken();
            porSesion.putIfAbsent(clave, rt);
        }
        List<SesionDispositivoResponse> lista = new ArrayList<>();
        porSesion.forEach((id, rt) -> lista.add(new SesionDispositivoResponse(
                id,
                rt.getDispositivo() != null ? rt.getDispositivo() : "Dispositivo sin identificar",
                rt.getIp(),
                rt.getInicio() != null ? rt.getInicio() : rt.getCreatedAt(),
                rt.getCreatedAt(),
                id.equals(actual))));
        // El dispositivo actual primero, luego por último uso.
        lista.sort((a, b) -> a.actual() != b.actual() ? (a.actual() ? -1 : 1)
                : b.ultimoUso().compareTo(a.ultimoUso()));
        return lista;
    }

    /** Cierra la sesión de un dispositivo de la persona en curso. */
    @Transactional
    public void cerrar(UUID sesionId) {
        User yo = userService.getCurrentUser();
        int n = refreshTokenRepository.revocarSesion(yo.getIdUser(), sesionId);
        if (n == 0) {
            throw new BadRequestException("Esa sesión ya no está abierta");
        }
        userRepository.invalidarTokensDeAcceso(yo.getIdUser(), OffsetDateTime.now());
        systemAuditService.record("SESSION_REVOKED", "User", yo.getIdUser());
        avisarDispositivos(yo);
    }

    /**
     * Pide a los navegadores de la persona que comprueben su sesión. El cerrado
     * se da cuenta al renovar (y sale con aviso); los demás renuevan sin notarlo.
     * Sin esto, el teléfono cerrado seguía mostrando la app hasta su siguiente petición.
     */
    private void avisarDispositivos(User yo) {
        UUID id = yo.getIdUser();
        Runnable aviso = () -> {
            try {
                sseEventBroker.publishAUsuarios(List.of(id), "sesion-revisar", Map.of());
            } catch (RuntimeException e) {
                // El aviso en vivo es una comodidad: el cierre ya está hecho.
            }
        };
        // Después del commit: si el navegador renovara antes, el cierre aún no se vería.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            aviso.run();
                        }
                    });
        } else {
            aviso.run();
        }
    }

    /** Cierra todas las sesiones menos la del dispositivo desde el que se pide. */
    @Transactional
    public int cerrarLasDemas(String refreshActual) {
        User yo = userService.getCurrentUser();
        UUID actual = authService.sesionDe(refreshActual);
        if (actual == null) {
            throw new BadRequestException("No se reconoce la sesión de este dispositivo; vuelva a iniciar sesión");
        }
        int n = refreshTokenRepository.revocarOtras(yo.getIdUser(), actual);
        if (n > 0) {
            userRepository.invalidarTokensDeAcceso(yo.getIdUser(), OffsetDateTime.now());
            systemAuditService.record("SESSION_REVOKED_OTHERS", "User", yo.getIdUser());
            avisarDispositivos(yo);
        }
        return n;
    }
}
