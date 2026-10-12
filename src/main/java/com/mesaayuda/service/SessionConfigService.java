package com.mesaayuda.service;

import com.mesaayuda.entity.IdsConfiguracion;
import com.mesaayuda.entity.SessionConfig;
import com.mesaayuda.repository.SessionConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de la duracion de sesion configurada. Se usa al crear el token y al
 * avisar al usuario de cuanto le queda, asi que se cachea unos segundos: sin
 * cache, cada login y cada peticion de perfil abriria una transaccion.
 */
@Service
public class SessionConfigService {

    private static final Logger log = LoggerFactory.getLogger(SessionConfigService.class);

    /** Tope y piso razonables para no dejar la app con un token inservible. */
    private static final int MIN_MINUTOS = 5;
    private static final int MAX_MINUTOS = 1440;

    private static final long CACHE_MS = 5_000L;

    @Autowired
    private SessionConfigRepository repository;

    /** Valor de respaldo si todavia no hay fila en la base. */
    @Value("${app.jwt.expiration-ms:900000}")
    private long defaultExpirationMs = 900_000L;

    private volatile Optional<SessionConfig> cache = Optional.empty();
    private volatile long cacheAt = 0L;

    @Transactional(readOnly = true)
    public SessionConfig config() {
        long ahora = System.currentTimeMillis();
        if (ahora - cacheAt < CACHE_MS) {
            return cache.orElse(null);
        }
        SessionConfig found = repository.findTopByOrderByIdSessionConfigAsc().orElse(null);
        if (found == null) {
            // Todavia no hay fila: se usa el valor del yml hasta que se cree.
            found = new SessionConfig();
            found.setIdSessionConfig(IdsConfiguracion.SESION);
            found.setSessionMinutes(minutosPorDefecto());
            found.setWarningMinutes(5);
            found.setAutoRenew(true);
            found.setIdleEnabled(true);
            found.setIdleMinutes(15);
        }
        cache = Optional.of(found);
        cacheAt = ahora;
        return found;
    }

    /** Vida del token de acceso, ya acotada. */
    @Transactional(readOnly = true)
    public Duration duracion() {
        SessionConfig c = config();
        int minutos = c == null || c.getSessionMinutes() == null
                ? minutosPorDefecto()
                : c.getSessionMinutes();
        return Duration.ofMinutes(acotar(minutos, MIN_MINUTOS, MAX_MINUTOS));
    }

    @Transactional(readOnly = true)
    public int warningMinutos() {
        SessionConfig c = config();
        if (c == null || c.getWarningMinutes() == null) {
            return 5;
        }
        return acotar(c.getWarningMinutes(), 1, MAX_MINUTOS);
    }

    @Transactional(readOnly = true)
    public boolean autoRenew() {
        SessionConfig c = config();
        return c == null || c.getAutoRenew() == null || c.getAutoRenew();
    }

    /** Si el cierre por inactividad esta activo. */
    @Transactional(readOnly = true)
    public boolean idleHabilitado() {
        SessionConfig c = config();
        return c == null || c.getIdleEnabled() == null || c.getIdleEnabled();
    }

    /** Minutos sin actividad antes de cerrar la sesion. */
    @Transactional(readOnly = true)
    public int idleMinutos() {
        SessionConfig c = config();
        if (c == null || c.getIdleMinutes() == null) {
            return 15;
        }
        return acotar(c.getIdleMinutes(), 1, MAX_MINUTOS);
    }

    @Transactional
    public SessionConfig actualizar(Integer sessionMinutes, Integer warningMinutes,
                                    Boolean autoRenew, Boolean idleEnabled, Integer idleMinutes,
                                    UUID updatedBy) {
        SessionConfig c = repository.findTopByOrderByIdSessionConfigAsc()
                .orElseGet(() -> {
                    SessionConfig nuevo = new SessionConfig();
                    nuevo.setIdSessionConfig(IdsConfiguracion.SESION);
                    return nuevo;
                });
        if (sessionMinutes != null) {
            c.setSessionMinutes(acotar(sessionMinutes, MIN_MINUTOS, MAX_MINUTOS));
        }
        if (warningMinutes != null) {
            c.setWarningMinutes(acotar(warningMinutes, 1, MAX_MINUTOS));
        }
        if (autoRenew != null) {
            c.setAutoRenew(autoRenew);
        }
        if (idleEnabled != null) {
            c.setIdleEnabled(idleEnabled);
        }
        if (idleMinutes != null) {
            c.setIdleMinutes(acotar(idleMinutes, 1, MAX_MINUTOS));
        }
        c.setUpdatedBy(updatedBy);
        SessionConfig guardada = repository.save(c);
        invalidarCache();
        log.info("Duracion de sesion actualizada a {} minutos (aviso a los {}), autoRenew={}, " +
                        "inactividad={} ({} minutos)",
                guardada.getSessionMinutes(), guardada.getWarningMinutes(), guardada.getAutoRenew(),
                guardada.getIdleEnabled(), guardada.getIdleMinutes());
        return guardada;
    }

    /** Se llama tras guardar para que el proximo login use el valor nuevo. */
    public void invalidarCache() {
        cache = Optional.empty();
        cacheAt = 0L;
    }

    /** Acota un valor al rango permitido, sin depender de Math.clamp. */
    private static int acotar(int valor, int min, int max) {
        return Math.max(min, Math.min(max, valor));
    }

    private static long acotar(long valor, int min, int max) {
        return Math.max(min, Math.min(max, valor));
    }

    private int minutosPorDefecto() {
        return (int) acotar(defaultExpirationMs / 60_000L, MIN_MINUTOS, MAX_MINUTOS);
    }
}
