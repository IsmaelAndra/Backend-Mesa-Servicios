package com.mesaayuda.service;

import com.mesaayuda.entity.LoginAttempt;
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
 * Contador de intentos fallidos y bloqueo temporal de una cuenta.
 *
 * Vive en su propia clase, y no como metodos privados de AuthService, por un
 * motivo que es el bug que arregla este servicio.
 *
 * AuthService es "@Transactional" a nivel de clase, y al fallar una contraseña
 * lanza BadCredentialsException. Una excepcion de tipo RuntimeException marca la
 * transaccion para rollback, asi que el save() del intento fallido se
 * deshacia: el contador nunca llegaba a la base y el bloqueo no existia. Cinco
 * intentosequivocados y uno mas no cambiaban nada, y el ataque por fuerza bruta
 * contra una cuenta era indefinido. Pasaba desapercibido porque el contador si se
 * incrementaba en memoria durante la misma peticion.
 *
 * La solucion es "Propagation.REQUIRES_NEW": el registro del intento abre su
 * propia transaccion y se confirma al terminar, aunque la de la llamada que lo
 * invoco termine en rollback. Por eso el metodo esta en otra clase: si estuviera
 * en AuthService, la would've llamado desde dentro de su propia transaccion y
 * "REQUIRED" (el valor por defecto) se uniria a ella sin isolamento alguno.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Intentos permitidos antes de bloquear la cuenta. */
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /** Duracion del bloqueo una vez alcanzado el limite. */
    private static final long LOCK_MINUTES = 15;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    /**
     * Registra un intento fallido y, si se alcanza el limite, bloquea la cuenta.
     *
     * El bloqueo se levanta solo al vencer los 15 minutos: no hay proceso de
     * desbloqueo, y el siguiente fallo volveria a bloquear la cuenta.
     *
     * Se confirma en su propia transaccion a proposito: el flujo que la llama
     * va a terminar en excepcion, y el registro tiene que sobrevivir a ese
     * rollback o el bloqueo no serviria de nada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(String key) {
        LoginAttempt attempt = loginAttemptRepository.findByUserKey(key).orElseGet(() -> {
            LoginAttempt nuevo = new LoginAttempt();
            nuevo.setUserKey(key);
            nuevo.setFailedCount(0);
            return nuevo;
        });

        int fallos = (attempt.getFailedCount() == null ? 0 : attempt.getFailedCount()) + 1;
        attempt.setFailedCount(fallos);

        if (fallos >= MAX_LOGIN_ATTEMPTS) {
            attempt.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_MINUTES));
            // Se reinicia el contador para que, al vencer el bloqueo, al que
            // siga fallando le haga falta de nuevo el numero completo de
            // intentos y no se bloquee al segundo fallo.
            attempt.setFailedCount(0);
            log.warn("Cuenta bloqueada {} intentos por {}", key, fallos);
        }

        loginAttemptRepository.save(attempt);
    }

    /** Minutos que quedan de bloqueo; 0 si la cuenta no esta bloqueada. */
    @Transactional(readOnly = true)
    public long minutosRestantes(LoginAttempt attempt) {
        if (!estaBloqueada(attempt)) {
            return 0;
        }
        return Duration.between(OffsetDateTime.now(), attempt.getLockedUntil()).toMinutes() + 1;
    }

    @Transactional(readOnly = true)
    public boolean estaBloqueada(LoginAttempt attempt) {
        return attempt != null
                && attempt.getLockedUntil() != null
                && attempt.getLockedUntil().isAfter(OffsetDateTime.now());
    }

    /**
     * Limpia el contador tras un acceso correcto.
     *
     * Va en su propia transaccion por el mismo motivo que registrarFallo, pero
     * aqui el efecto es el contrario: si el resto del login fallara despues
     * (por ejemplo, alARM si la cuenta esta inactiva), el borrado ya esta
     * confirmado y no se arrastra con el rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limpiar(String key) {
        loginAttemptRepository.findByUserKey(key).ifPresent(loginAttemptRepository::delete);
    }

    /**
     * Busca el registro para poder decidir si la cuenta esta bloqueada.
     *
     * El flujo que la llama todavia puede terminar en rollback; por eso solo
     * lee y no escribe nada. Spring abre una transaccion que se confirma sola, y
     * eso no es un problema: el que se deshacia era el save del intento fallido.
     */
    @Transactional(readOnly = true)
    public LoginAttempt buscar(String key) {
        return loginAttemptRepository.findByUserKey(key).orElse(null);
    }
}