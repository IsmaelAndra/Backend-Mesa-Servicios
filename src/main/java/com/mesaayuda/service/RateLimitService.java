package com.mesaayuda.service;

import com.mesaayuda.entity.LoginAttempt;
import com.mesaayuda.exception.TooManyRequestsException;
import com.mesaayuda.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Límite de intentos por acción, para frenar la fuerza bruta y el abuso de los
 * formularios públicos.
 * <p>
 * Reutiliza la tabla {@code login_attempt}, que ya lleva el contador y la
 * hora de desbloqueo del bloqueo por intentos de acceso. La clave se compone
 * con el prefijo de la acción, así que el bloqueo de "restablecer contraseña" no
 * interfiere con el del inicio de sesión.
 * <p>
 * Lo más importante era el restablecimiento de contraseña: antes no tenía
 * ningún límite, de modo que se podía pedir el reinicio de una cuenta conocida
 * tantas veces como se quisiera, y cada intento mandaba un correo. También
 * respondía diciendo si el correo existía, lo que permite enumerar la plantilla
 * de correos de la organización.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    /** Restablecer contraseña: 5 por hora y por objetivo. */
    public static final int MAX_RESTABLECER = 5;
    public static final Duration VENTANA_RESTABLECER = Duration.ofHours(1);

    /**
     * Inicio de sesión fallido por IP. El bloqueo por cuenta ya existe
     * (LoginAttemptService), pero no frena a quien prueba una contraseña común
     * contra muchas cuentas distintas. El cupo es holgado a propósito: en la
     * cooperativa muchas personas salen a Internet por la misma IP de oficina.
     */
    public static final int MAX_LOGIN_ORIGEN = 30;
    public static final Duration VENTANA_LOGIN_ORIGEN = Duration.ofMinutes(15);

    /** Auto-registro por IP: es público y cada alta crea una cuenta. */
    public static final int MAX_REGISTRO_ORIGEN = 10;
    public static final Duration VENTANA_REGISTRO_ORIGEN = Duration.ofHours(1);

    /**
     * Códigos de doble factor fallidos por usuario. Sin límite, un desafío
     * admitía intentos ilimitados contra un espacio de un millón de códigos.
     */
    public static final int MAX_MFA = 5;
    public static final Duration VENTANA_MFA = Duration.ofMinutes(15);

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    /**
     * Lanza si esta combinación de acción y clave ya agotó su cupo. Se llama
     * ANTES de hacer nada, y el registro del intento va aparte, para poder
     * contar solo lo que de verdad se intentó.
     */
    @Transactional(readOnly = true)
    public void exigir(String accion, String clave, int maxIntentos, Duration ventana) {
        String userKey = componer(accion, clave);
        LoginAttempt registro = loginAttemptRepository.findByUserKey(userKey).orElse(null);
        if (registro == null) {
            return;
        }
        OffsetDateTime hasta = registro.getLockedUntil();
        if (hasta != null && hasta.isAfter(OffsetDateTime.now())) {
            long minutos = Math.max(1, Duration.between(OffsetDateTime.now(), hasta).toMinutes());
            log.warn("Límite de '{}' alcanzado para {}; bloqueado {} min", accion, clave, minutos);
            throw new TooManyRequestsException(
                    "Demasiados intentos. Inténtalo de nuevo en " + minutos + " minuto(s).");
        }
        // La ventana caducó: se reinicia el contador para no arrastrar el
        // histórico de hace días.
        if (registro.getFailedCount() >= maxIntentos && hasta == null) {
            registro.setFailedCount(0);
        }
    }

    /** Suma un intento y bloquea cuando se supera el máximo. */
    @Transactional
    public void registrar(String accion, String clave, int maxIntentos, Duration ventana) {
        String userKey = componer(accion, clave);
        LoginAttempt registro = loginAttemptRepository.findByUserKey(userKey).orElseGet(() -> {
            LoginAttempt nuevo = new LoginAttempt();
            nuevo.setUserKey(userKey);
            nuevo.setFailedCount(0);
            return nuevo;
        });

        // El cupo es "N por ventana", no "N para siempre": si el último intento
        // es de antes de la ventana, se empieza de cero. Sin esto, los fallos
        // sueltos de días distintos se iban sumando hasta bloquear sin motivo.
        if (registro.getUpdatedAt() != null
                && registro.getUpdatedAt().isBefore(OffsetDateTime.now().minus(ventana))) {
            registro.setFailedCount(0);
        }
        int cuenta = (registro.getFailedCount() == null ? 0 : registro.getFailedCount()) + 1;
        registro.setFailedCount(cuenta);

        if (cuenta >= maxIntentos) {
            registro.setLockedUntil(OffsetDateTime.now().plus(ventana));
            registro.setFailedCount(0);
            log.warn("Bloqueada la acción '{}' para {} durante {}", accion, clave, ventana);
        }
        loginAttemptRepository.save(registro);
    }

    /**
     * Igual que {@link #registrar}, pero en su propia transacción. Es para
     * contar un fallo justo antes de lanzar la excepción que lo rechaza: con la
     * transacción del llamante, el rollback de ese rechazo borraría también el
     * intento y el límite no llegaría a aplicarse nunca.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAparte(String accion, String clave, int maxIntentos, Duration ventana) {
        registrar(accion, clave, maxIntentos, ventana);
    }

    /** Crea la fila en cero, para que el primer intento ya quede contabilizado. */
    @Transactional
    public void preparar(String accion, String clave) {
        String userKey = componer(accion, clave);
        if (!loginAttemptRepository.findByUserKey(userKey).isPresent()) {
            LoginAttempt registro = new LoginAttempt();
            registro.setUserKey(userKey);
            registro.setFailedCount(0);
            loginAttemptRepository.save(registro);
        }
    }

    /** Borra el historial: se llama cuando la acción tiene éxito. */
    @Transactional
    public void limpiar(String accion, String clave) {
        loginAttemptRepository.findByUserKey(componer(accion, clave))
                .ifPresent(loginAttemptRepository::delete);
    }

    private String componer(String accion, String clave) {
        String k = accion + "|" + (clave == null ? "" : clave.trim().toLowerCase());
        // La columna es de 150 caracteres: se recorta para no fallar por claves
        // largas (direcciones IPv6 o correos con muchos alias).
        return k.length() > 150 ? k.substring(0, 150) : k;
    }
}
