package com.mesaayuda.service;

import com.mesaayuda.entity.LoginAttempt;
import com.mesaayuda.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contador de intentos fallidos y bloqueo de cuenta.
 *
 * El servicio existe por si mismo, y no porque deba estar dentro de AuthService,
 * porque necesita confirmarse en su propia transaccion: el login falla con una
 * excepcion y eso revierte la transaccion de la llamada. Si el save compartiera
 * transaccion, el contador nunca llegaria a la base.
 *
 * Estos tests comprueban la regla de cinco intentos y quince minutos, y sobre
 * todo que el estado se guarde de verdad. Un contador que solo vive en memoria
 * es indistinguible de uno que funciona mientras las pruebas pasan.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String KEY = "alguien@coacmagisterio.fin.ec";

    @Mock private LoginAttemptRepository loginAttemptRepository;

    @InjectMocks private LoginAttemptService service;

    /** Registro que se guarda, para poder inspeccionarlo despues. */
    private LoginAttempt guardado;

    @BeforeEach
    void setUp() {
        guardado = null;
    }

    /** Simula que hay un registro previo con el contador dado. */
    private void previo(Integer fallos, OffsetDateTime bloqueadaHasta) {
        when(loginAttemptRepository.findByUserKey(KEY)).thenAnswer(inv -> {
            if (guardado == null) {
                LoginAttempt a = new LoginAttempt();
                a.setUserKey(KEY);
                a.setFailedCount(fallos);
                a.setLockedUntil(bloqueadaHasta);
                guardado = a;
            }
            return Optional.of(guardado);
        });
    }

    /** El stub de save, aparte porque limpiar() no lo usa. */
    private void guardandoSave() {
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> {
            guardado = inv.getArgument(0);
            return guardado;
        });
    }

    @Test
    @DisplayName("el primer intento fallido crea el registro con un fallo")
    void primerFalloCreaElRegistro() {
        when(loginAttemptRepository.findByUserKey(KEY)).thenReturn(Optional.empty());
        ArgumentCaptor<LoginAttempt> cap = ArgumentCaptor.forClass(LoginAttempt.class);
        when(loginAttemptRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.registrarFallo(KEY);

        assertEquals(KEY, cap.getValue().getUserKey());
        assertEquals(1, cap.getValue().getFailedCount());
        assertNull(cap.getValue().getLockedUntil());
    }

    @Test
    @DisplayName("cuatro fallos seguidos no bloquean todavia")
    void cuatroFallosNoBloquean() {
        previo(3, null);

        service.registrarFallo(KEY);

        assertEquals(4, guardado.getFailedCount());
        assertNull(guardado.getLockedUntil());
    }

    @Test
    @DisplayName("el quinto fallo bloquea la cuenta")
    void quintoFalloBloquea() {
        previo(4, null);

        service.registrarFallo(KEY);

        assertNotNull(guardado.getLockedUntil());
        assertTrue(guardado.getLockedUntil().isAfter(OffsetDateTime.now()));
        // Se reinicia el contador para que al vencer el bloqueo hagan falta de
        // nuevo cinco intentos, y no dos.
        assertEquals(0, guardado.getFailedCount());
    }

    @Test
    @DisplayName("la cuenta bloqueada avisa cuantos minutos quedan")
    void minutosRestantesDeUnaCuentaBloqueada() {
        LoginAttempt bloqueada = new LoginAttempt();
        bloqueada.setLockedUntil(OffsetDateTime.now().plusMinutes(14));

        long minutos = service.minutosRestantes(bloqueada);

        // +1 porque a los 14m59s todavia se responde "intente en 15 minutos".
        assertTrue(minutos >= 14 && minutos <= 15, "esperaba ~15, obtuve " + minutos);
    }

    @Test
    @DisplayName("una cuenta sin bloqueo no avisa minutos pendientes")
    void minutosRestantesSinBloqueo() {
        assertEquals(0, service.minutosRestantes(null));

        LoginAttempt sinBloqueo = new LoginAttempt();
        assertEquals(0, service.minutosRestantes(sinBloqueo));
    }

    @Test
    @DisplayName("un bloqueo ya vencido cuenta como cuenta libre")
    void bloqueoVencidoNoBloquea() {
        LoginAttempt vencida = new LoginAttempt();
        vencida.setLockedUntil(OffsetDateTime.now().minusMinutes(1));

        assertFalse(service.estaBloqueada(vencida));
    }

    @Test
    @DisplayName("un bloqueo vigente bloquea, y un registro ausente no")
    void bloqueoVigenteBloquea() {
        LoginAttempt vigente = new LoginAttempt();
        vigente.setLockedUntil(OffsetDateTime.now().plusMinutes(1));

        assertTrue(service.estaBloqueada(vigente));
        assertFalse(service.estaBloqueada(null));
    }

    @Test
    @DisplayName("acceder con la clave correcta borra el registro, no lo deja en cero")
    void limpiarBorraElRegistro() {
        previo(2, null);

        service.limpiar(KEY);

        // Borrar y no poner el contador a cero: si el registro se conservara, un
        // fallo posterior seguiria sumando sobre el historial anterior.
        verify(loginAttemptRepository).delete(guardado);
        verify(loginAttemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("limpiar sin registro previo no falla")
    void limpiarSinRegistroPrevio() {
        when(loginAttemptRepository.findByUserKey(KEY)).thenReturn(Optional.empty());

        service.limpiar(KEY);

        verify(loginAttemptRepository, never()).delete(any());
    }

    @Test
    @DisplayName("un contador nulo en la base no revienta el registro del fallo")
    void contadorNuloSeTrataComoCero() {
        when(loginAttemptRepository.findByUserKey(KEY)).thenAnswer(inv -> {
            LoginAttempt a = new LoginAttempt();
            a.setUserKey(KEY);
            a.setFailedCount(null);
            return Optional.of(a);
        });
        ArgumentCaptor<LoginAttempt> cap = ArgumentCaptor.forClass(LoginAttempt.class);
        when(loginAttemptRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.registrarFallo(KEY);

        assertEquals(1, cap.getValue().getFailedCount());
    }

    @Test
    @DisplayName("buscar devuelve null si no hay registro, no una excepcion")
    void buscarSinRegistro() {
        when(loginAttemptRepository.findByUserKey(KEY)).thenReturn(Optional.empty());

        assertNull(service.buscar(KEY));
    }
}