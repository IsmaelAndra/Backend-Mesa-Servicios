package com.mesaayuda.service;

import com.mesaayuda.entity.LoginAttempt;
import com.mesaayuda.exception.TooManyRequestsException;
import com.mesaayuda.repository.LoginAttemptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El restablecimiento de contraseña era el endpoint público más abusable: sin
 * límite, se podía pedir el reinicio de una cuenta conocida tantas veces como
 * se quisiera, y cada intento mandaba un correo. Aquí se fija que se bloquea.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private static final String CORREO = "juan@coacmagisterio.fin.ec";
    private static final String CLAVE = "forgot-password|" + CORREO;
    private static final int MAX = 5;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @InjectMocks
    private RateLimitService service;

    private LoginAttempt registroCon(int fallidos, OffsetDateTime hasta) {
        LoginAttempt r = new LoginAttempt();
        r.setUserKey(CLAVE);
        r.setFailedCount(fallidos);
        r.setLockedUntil(hasta);
        return r;
    }

    @Test
    @DisplayName("el primer intento pasa: no hay registro todavía")
    void primerIntentoPasa() {
        when(loginAttemptRepository.findByUserKey(CLAVE)).thenReturn(Optional.empty());

        assertThatCode(() -> service.exigir("forgot-password", CORREO, MAX, Duration.ofHours(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("al superar el máximo, la siguiente petición se bloquea")
    void bloqueaAlSuperarElMaximo() {
        when(loginAttemptRepository.findByUserKey(CLAVE)).thenReturn(Optional.of(registroCon(MAX - 1, null)));
        LoginAttempt[] guardado = new LoginAttempt[1];
        lenient().when(loginAttemptRepository.save(any(LoginAttempt.class)))
                .thenAnswer(i -> { guardado[0] = i.getArgument(0); return guardado[0]; });

        service.registrar("forgot-password", CORREO, MAX, Duration.ofHours(1));

        assertThat(guardado[0].getLockedUntil()).isNotNull();
        assertThat(guardado[0].getLockedUntil()).isAfter(OffsetDateTime.now());
        // Al bloquear se reinicia el contador, para que al vencer la ventana
        // empiece de cero en vez de re-bloquearse al instante.
        assertThat(guardado[0].getFailedCount()).isZero();
    }

    @Test
    @DisplayName("con el bloqueo activo, exigir lanza TooManyRequests")
    void exigirLanzaConBloqueoActivo() {
        when(loginAttemptRepository.findByUserKey(CLAVE))
                .thenReturn(Optional.of(registroCon(0, OffsetDateTime.now().plusMinutes(30))));

        assertThatThrownBy(() -> service.exigir("forgot-password", CORREO, MAX, Duration.ofHours(1)))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Demasiados intentos");
    }

    @Test
    @DisplayName("el bloqueo cuenta los minutos que faltan, como mínimo 1")
    void elMensajeIndicaLosMinutosQueFaltan() {
        // Menos de un minuto: si se informara de 0, el mensaje sería confuso.
        when(loginAttemptRepository.findByUserKey(CLAVE))
                .thenReturn(Optional.of(registroCon(0, OffsetDateTime.now().plusSeconds(20))));

        assertThatThrownBy(() -> service.exigir("forgot-password", CORREO, MAX, Duration.ofHours(1)))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("1 minuto");
    }

    @Test
    @DisplayName("caducado el bloqueo, se puede volver a intentar")
    void bloqueoCaducadoPermiteDeNuevo() {
        when(loginAttemptRepository.findByUserKey(CLAVE))
                .thenReturn(Optional.of(registroCon(9, OffsetDateTime.now().minusMinutes(5))));

        assertThatCode(() -> service.exigir("forgot-password", CORREO, MAX, Duration.ofHours(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("las acciones distintas llevan contadores separados")
    void accionesSeparadas() {
        when(loginAttemptRepository.findByUserKey(any())).thenReturn(Optional.empty());
        lenient().when(loginAttemptRepository.save(any(LoginAttempt.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.registrar("forgot-password", CORREO, MAX, Duration.ofHours(1));
        service.registrar("forgot-password-origen", CORREO, 20, Duration.ofHours(1));

        // Dos filas distintas: el prefijo de la acción forma parte de la clave.
        verify(loginAttemptRepository, times(2)).save(any(LoginAttempt.class));
    }

    @Test
    @DisplayName("la clave se normaliza a minúsculas y sin espacios")
    void normalizaLaClave() {
        when(loginAttemptRepository.findByUserKey(any())).thenReturn(Optional.empty());
        LoginAttempt[] guardado = new LoginAttempt[1];
        lenient().when(loginAttemptRepository.save(any(LoginAttempt.class)))
                .thenAnswer(i -> { guardado[0] = i.getArgument(0); return guardado[0]; });

        service.registrar("forgot-password", "  Juan@COAC.GOB.EC  ", MAX, Duration.ofHours(1));

        assertThat(guardado[0].getUserKey()).isEqualTo("forgot-password|juan@coac.gob.ec");
    }

    @Test
    @DisplayName("una clave muy larga se recorta y no revienta contra la columna de 150")
    void recortaClavesLargas() {
        when(loginAttemptRepository.findByUserKey(any())).thenReturn(Optional.empty());
        LoginAttempt[] guardado = new LoginAttempt[1];
        lenient().when(loginAttemptRepository.save(any(LoginAttempt.class)))
                .thenAnswer(i -> { guardado[0] = i.getArgument(0); return guardado[0]; });

        service.registrar("forgot-password", "a".repeat(400), MAX, Duration.ofHours(1));

        assertThat(guardado[0].getUserKey()).hasSize(150);
    }

    @Test
    @DisplayName("exitos limpia el historial del objetivo")
    void limpiarBorraElHistorial() {
        LoginAttempt r = registroCon(2, null);
        when(loginAttemptRepository.findByUserKey(CLAVE)).thenReturn(Optional.of(r));

        service.limpiar("forgot-password", CORREO);

        verify(loginAttemptRepository).delete(r);
    }

    @Test
    @DisplayName("los fallos de fuera de la ventana no se suman: el cupo es por ventana")
    void fallosViejosNoCuentan() {
        LoginAttempt viejo = registroCon(MAX - 1, null);
        viejo.setUpdatedAt(OffsetDateTime.now().minusHours(3));
        when(loginAttemptRepository.findByUserKey(CLAVE)).thenReturn(Optional.of(viejo));
        LoginAttempt[] guardado = new LoginAttempt[1];
        when(loginAttemptRepository.save(any(LoginAttempt.class)))
                .thenAnswer(i -> { guardado[0] = i.getArgument(0); return guardado[0]; });

        service.registrar("forgot-password", CORREO, MAX, Duration.ofHours(1));

        // Sin el reinicio, este intento habría sido el quinto y bloquearía.
        assertThat(guardado[0].getLockedUntil()).isNull();
        assertThat(guardado[0].getFailedCount()).isEqualTo(1);
    }
}
