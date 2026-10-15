package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.MfaChallengeResponse;
import com.mesaayuda.dto.MfaSettingResponse;
import com.mesaayuda.entity.MfaChallenge;
import com.mesaayuda.entity.MfaRecoveryCode;
import com.mesaayuda.entity.MfaSetting;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.UserMfa;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.MfaChallengeRepository;
import com.mesaayuda.repository.MfaRecoveryCodeRepository;
import com.mesaayuda.repository.MfaSettingRepository;
import com.mesaayuda.repository.UserMfaRepository;
import com.mesaayuda.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * El segundo factor tiene que impedir de verdad el acceso sin él.
 *
 * <p>El riesgo de este tipo de funcionalidad es que se implemente sin que nadie lo note:
 * el código compila, el login "funciona", y en realidad basta la contraseña porque
 * los tokens se emiten antes de mirar el código. Por eso casi todas las pruebas
 * de aquí comprueban la ausencia de sesión, no la presencia de tokens.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaServiceSeguridadTest {

    private static final String CORREO = "agente@coacmagisterio.fin.ec";

    @Mock private MfaSettingRepository settingRepository;
    @Mock private UserMfaRepository userMfaRepository;
    @Mock private MfaChallengeRepository challengeRepository;
    @Mock private MfaRecoveryCodeRepository recoveryCodeRepository;
    @Mock private TotpService totp;
    @Mock private SecretCryptoService secretCrypto;
    @Mock private SystemAuditService systemAuditService;
    @Mock private RateLimitService rateLimitService;

    @InjectMocks private MfaService mfa;

    private final TotpService totpReal = new TotpService();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private User usuario;
    private final java.util.Map<String, MfaChallenge> desafios = new HashMap<>();

    @BeforeEach
    void setUp() {
        // El TotpService real: un mock devolvería true para siempre y las
        // pruebas pasarían sin comprobar nada.
        ReflectionTestUtils.setField(mfa, "totp", totpReal);
        ReflectionTestUtils.setField(mfa, "passwordEncoder", encoder);

Role rol = new Role();
        rol.setNameRole("AGENT");
        // Un mock de save() sin stub devuelve null, y registro() usa lo que le
        // devuelven para seguir trabajando. Sin esto, toda alta revienta con NPE.
        org.mockito.Mockito.when(userMfaRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        usuario = new User();
        usuario.setIdUser(id(50));
        usuario.setCorporateEmailUser(CORREO);
        usuario.setPasswordUser(encoder.encode("MiClaveActual123!"));
        usuario.setRolUser(rol);
        usuario.setStatusUser("ACTIVE");
        usuario.setDeletedUser(false);
    }

    private void mfaActivado() {
        MfaSetting s = new MfaSetting();
        s.setId(MfaSetting.UNICO);
        s.setMfaEnabled(true);
        s.setIssuerMfa("Mesa de Servicios");
        org.mockito.Mockito.when(settingRepository.findById(MfaSetting.UNICO)).thenReturn(Optional.of(s));
    }

    private void mfaApagado() {
        MfaSetting s = new MfaSetting();
        s.setId(MfaSetting.UNICO);
        s.setMfaEnabled(false);
        org.mockito.Mockito.when(settingRepository.findById(MfaSetting.UNICO)).thenReturn(Optional.of(s));
    }

    private UserMfa enrolado(String secreto) {
        UserMfa m = new UserMfa();
        m.setUser(usuario);
        m.setSecretAlmacenado("cifrado:" + secreto);
        m.setEnrolled(true);
        m.setCreadoAtUserMfa(OffsetDateTime.now());
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50))).thenReturn(Optional.of(m));
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado:" + secreto)).thenReturn(secreto);
        return m;
    }

    // ============================ interruptor ============================

    @Test
    @DisplayName("repetir el acceso con el alta a medias muestra el MISMO QR")
    void altaPendienteReutilizaElSecreto() {
        // Antes cada login generaba un secreto nuevo: quien escaneaba, volvía
        // atrás y escaneaba otra vez quedaba con dos cuentas en el Authenticator.
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = new UserMfa();
        m.setUser(usuario);
        m.setEnrolled(false);
        m.setSecretPendiente("cifrado:" + secreto);
        m.setCreadoAtUserMfa(OffsetDateTime.now().minusMinutes(10));
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50))).thenReturn(Optional.of(m));
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado:" + secreto)).thenReturn(secreto);

        MfaChallengeResponse r = mfa.prepararDesafio(usuario);

        assertEquals(MfaChallengeResponse.STATUS_ENROLAR, r.status());
        assertTrue(r.qrUri().contains("secret=" + secreto), "el QR debe llevar el secreto ya escaneado");
        assertEquals("cifrado:" + secreto, m.getSecretPendiente());
    }

    @Test
    @DisplayName("un alta a medias de hace más de un día genera QR nuevo")
    void altaPendienteCaducadaGeneraOtroSecreto() {
        mfaActivado();
        String viejo = totpReal.generarSecreto();
        UserMfa m = new UserMfa();
        m.setUser(usuario);
        m.setEnrolled(false);
        m.setSecretPendiente("cifrado:" + viejo);
        m.setCreadoAtUserMfa(OffsetDateTime.now().minusDays(2));
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50))).thenReturn(Optional.of(m));
        org.mockito.Mockito.when(secretCrypto.encrypt(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> "cifrado:" + inv.getArgument(0));

        MfaChallengeResponse r = mfa.prepararDesafio(usuario);

        assertFalse(r.qrUri().contains("secret=" + viejo));
    }

    @Test
    @DisplayName("con el interruptor apagado, el login no exige nada")
    void apagadoNoExigeNada() {
        mfaApagado();

        assertFalse(mfa.activo());
        assertNull(mfa.prepararDesafio(usuario),
                "sin doble factor el login va directo: es lo que hay que asegurar");
    }

    @Test
    @DisplayName("con el interruptor encendido, el login devuelve desafio y NO tokens")
    void encendidoExigeDesafio() {
        mfaActivado();
        enrolado(totpReal.generarSecreto());

        MfaChallengeResponse r = mfa.prepararDesafio(usuario);

        assertNotNull(r, "tiene que haber desafio");
        assertEquals(MfaChallengeResponse.STATUS_VERIFICAR, r.status());
        assertNotNull(r.challenge());
        // Lo importante: no hay nada con lo que abrir sesión todavía.
        assertNull(r.qrUri(), "quien ya está enrolado no recibe clave nueva");
        assertNull(r.claveManual());
    }

@Test
    @DisplayName("el super administrador TAMBIEN tiene que confirmar el código")
    void elSuperAdminTambienExigeCodigo() {
        // Antes esta cuenta quedaba exenta, y era justo el error: la de más poder
        // era la única que se entraba con un solo factor. Una cuenta con más
        // permisos que ninguna es la que más importa proteger.
        //
        // El riesgo de encerrar a todos se cubre por otro lado: cualquier cuenta
        // recibe códigos de recuperación en el alta, y desactivar el doble factor
        // exige contraseña Y un código vigente.
        mfaActivado();
        usuario.getRolUser().setNameRole("SUPER_ADMIN");
        enrolado(totpReal.generarSecreto());
  
        MfaChallengeResponse r = mfa.prepararDesafio(usuario);
  
        assertNotNull(r, "el super administrador no puede entrar con un solo factor");
        assertEquals(MfaChallengeResponse.STATUS_VERIFICAR, r.status());
        assertNull(r.qrUri(), "si ya esta enrolado, no le llega una clave nueva");
    }

    @Test
    @DisplayName("el super administrador sin app tambien recibe QR para enrolarse")
    void elSuperAdminSinAppSeEnrola() {
        mfaActivado();
        usuario.getRolUser().setNameRole("SUPER_ADMIN");
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50)))
                .thenReturn(Optional.empty());
  
        MfaChallengeResponse r = mfa.prepararDesafio(usuario);
  
        assertEquals(MfaChallengeResponse.STATUS_ENROLAR, r.status());
        assertNotNull(r.qrUri());
    }

    @Test
    @DisplayName("activar el interruptor avisa si el super administrador aun no tiene app")
    void activarAvisaSiElSuperAdminNoEstaEnrolado() {
        mfaActivado();
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(userMfaRepository.countByEnrolledTrue()).thenReturn(3L);
  
        MfaSettingResponse r = mfa.cambiar(true, null, id(50));
  
        assertTrue(r.mfaEnabled());
        assertFalse(r.superAdminEnrolled(),
                "la pantalla tiene que poder avisar de esto antes de que se quede fuera");
    }

    @Test
    @DisplayName("un usuario sin app recibe QR y clave para el alta")
    void sinAppRecibeQrYClave() {
        mfaActivado();
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50))).thenReturn(Optional.empty());

        MfaChallengeResponse r = mfa.prepararDesafio(usuario);

        assertEquals(MfaChallengeResponse.STATUS_ENROLAR, r.status());
        assertNotNull(r.qrUri(), "sin QR no hay alta");
        assertTrue(r.qrUri().startsWith("otpauth://totp/"), r.qrUri());
        assertNotNull(r.claveManual());
        assertTrue(r.claveManual().replace(" ", "").length() >= 32, "clave demasiado corta");
    }

    @Test
    @DisplayName("el secreto del alta se guarda cifrado, y como pendiente")
    void elSecretoPendienteVaCifrado() {
        mfaActivado();
        org.mockito.Mockito.when(userMfaRepository.findByUser_IdUser(id(50))).thenReturn(Optional.empty());
        org.mockito.Mockito.when(secretCrypto.encrypt(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("CIFRADO");

        mfa.prepararDesafio(usuario);

// registro() tambien guarda la fila recien creada antes de marcarla pendiente,
        // asi que save() se llama dos veces: por eso times(2) y no verify a secas.
        ArgumentCaptor<UserMfa> g = ArgumentCaptor.forClass(UserMfa.class);
        org.mockito.Mockito.verify(userMfaRepository, org.mockito.Mockito.atLeastOnce()).save(g.capture());
        UserMfa guardado = g.getAllValues().stream()
                .filter(u -> u.getSecretPendiente() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se guardo ningun secreto pendiente"));
        assertEquals("CIFRADO", guardado.getSecretPendiente());
        // Lo que decide si el usuario puede entrar no es que tenga un secreto
        // guardado, sino que haya confirmado uno.
        assertFalse(guardado.isEnrolled());
        assertNull(guardado.getSecretAlmacenado());
    }

    @Test
    @DisplayName("pedir un desafio nuevo invalida los anteriores")
    void elDesafioNuevoInvalidaLosViejos() {
        // Si no, abrir dos pestanas deja dos desafios vivos y el segundo se
        // podria confirmar mucho despues, con la clave expuesta todo ese rato.
        mfaActivado();
        enrolado(totpReal.generarSecreto());

        mfa.prepararDesafio(usuario);

        org.mockito.Mockito.verify(challengeRepository).invalidarVivos(
                org.mockito.ArgumentMatchers.eq(id(50)), org.mockito.ArgumentMatchers.any());
    }

    // ============================ confirmacion ============================

    @Test
    @DisplayName("confirmar el alta con un codigo valido enrola y entrega codigos de recuperacion")
    void altaCorrectaEnrola() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = enrolado(secreto);
        m.setEnrolled(false);
        m.setSecretPendiente("cifrado-pendiente");
        m.setSecretAlmacenado(null);
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado-pendiente")).thenReturn(secreto);

        String desafio = crearDesafio(MfaChallenge.PROPOSITO_ENROLL);
        List<String> recuperacion = mfa.confirmar(desafio, totpReal.calcularCodigo(secreto), null);

assertTrue(m.isEnrolled(), "tras confirmar un codigo ya queda enrolado");
            assertNull(m.getSecretPendiente(), "el pendiente pasa a definitivo");
            assertEquals(10, recuperacion.size(), "deben entregarse 10 codigos de recuperacion");

            // Esto faltaba y por eso el fallo se coló hasta la prueba contra el
            // servidor real: sin consumir el desafío, la emisión de sesión lo
            // rechazaba y el alta se quedaba a medias aunque el código valiera.
            assertNotNull(desafios.get(desafio).getUsedAt(),
                    "el alta debe gastar el desafio: si no, el mismo QR permitiria otro alta");
        }

    @Test
    @DisplayName("el codigo de recuperacion que se muestra al usuario sirve tal cual")
    void elCodigoDeRecuperacionMostradoSirve() {
        // Esta es la prueba que faltaba y que destapo un fallo real: el codigo se
        // guardaba con guion y se buscaba sin guion, así que ningun codigo de
        // recuperacion servia para nada. Las pruebas anteriores montaban el hash
        // a mano y nunca cruzaban generación con verificación, que es justo donde
        // estaba el fallo.
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = enrolado(secreto);
        m.setEnrolled(false);
        m.setSecretPendiente("cifrado-pendiente");
        m.setSecretAlmacenado(null);
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado-pendiente")).thenReturn(secreto);

        List<MfaRecoveryCode> guardados = new ArrayList<>();
        org.mockito.Mockito.when(recoveryCodeRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    guardados.add(inv.getArgument(0));
                    return inv.getArgument(0);
                });
        List<MfaRecoveryCode> vivos = new ArrayList<>();
        org.mockito.Mockito.when(recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(id(50)))
                .thenAnswer(inv -> vivos);

        List<String> mostrados = mfa.confirmar(crearDesafio(MfaChallenge.PROPOSITO_ENROLL),
                totpReal.calcularCodigo(secreto), null);

        assertEquals(10, mostrados.size());
        String primero = mostrados.get(0);
        assertTrue(primero.contains("-"), "al usuario se le muestra con guion: " + primero);
        assertEquals(64, guardados.get(0).getCodeHash().length(),
                "lo que se guarda es un hash SHA-256, no el codigo");
        vivos.addAll(guardados);

        // Ahora se usa el primero, tal cual se le dio al usuario.
        String desafio = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        assertDoesNotThrow(() -> mfa.confirmar(desafio, null, primero),
                "el codigo que se muestra tiene que servir: " + primero);
        assertNotNull(guardados.get(0).getUsadoAt(), "y debe quedar marcado como usado");
    }

    @Test
    @DisplayName("reutilizar el desafio del alta ya no enrolla otra vez")
    void elDesafioDelAltaNoSirveDosVeces() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = enrolado(secreto);
        m.setEnrolled(false);
        m.setSecretPendiente("cifrado-pendiente");
        m.setSecretAlmacenado(null);
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado-pendiente")).thenReturn(secreto);

        String desafio = crearDesafio(MfaChallenge.PROPOSITO_ENROLL);
        mfa.confirmar(desafio, totpReal.calcularCodigo(secreto), null);

        // Segundo intento con el mismo desafío y un código nuevo.
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado:")).thenReturn(secreto);
        assertThrows(BadRequestException.class,
                () -> mfa.confirmar(desafio, totpReal.calcularCodigo(secreto), null),
                "un desafio gastado no puede volver a enrolar");
    }

    @Test
    @DisplayName("confirmar el alta con un codigo incorrecto deja la cuenta SIN enrolar")
    void altaIncorrectaNoEnrola() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = enrolado(secreto);
        m.setEnrolled(false);
        m.setSecretPendiente("cifrado-pendiente");
        m.setSecretAlmacenado(null);
        org.mockito.Mockito.when(secretCrypto.decrypt("cifrado-pendiente")).thenReturn(secreto);

        String desafio = crearDesafio(MfaChallenge.PROPOSITO_ENROLL);

        assertThrows(BadRequestException.class, () -> mfa.confirmar(desafio, "000000", null));
        assertFalse(m.isEnrolled(), "un codigo incorrecto no puede enrolar a nadie");
    }

    @Test
    @DisplayName("el codigo de la app vale una vez y no mas")
    void elCodigoNoSeReutiliza() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        enrolado(secreto);
        String desafio = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        String codigo = totpReal.calcularCodigo(secreto);

        // La app muestra el mismo codigo 30 s y la tolerancia llega a un paso
        // atras: sin el bloqueo, ese codigo serviria casi dos minutos.
        mfa.confirmar(desafio, codigo, null);
        crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        assertThrows(BadRequestException.class, () -> mfa.confirmar(desafio, codigo, null),
                "el mismo codigo no puede servir dos veces");
    }

    @Test
    @DisplayName("codigo incorrecto en el login se rechaza y queda auditado")
    void codigoIncorrectoSeRechaza() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        enrolado(secreto);
        String desafio = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);

        assertThrows(BadRequestException.class, () -> mfa.confirmar(desafio, "999999", null));
        org.mockito.Mockito.verify(systemAuditService).record(
                org.mockito.ArgumentMatchers.eq("MFA_FAILED"), org.mockito.ArgumentMatchers.eq("User"),
                org.mockito.ArgumentMatchers.eq(id(50)));
    }

    @Test
    @DisplayName("un desafio ya usado no vale")
    void unDesafioUsadoNoVale() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        enrolado(secreto);
String token = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        desafios.get(token).setUsedAt(OffsetDateTime.now().minusSeconds(10));

        assertThrows(BadRequestException.class,
                () -> mfa.confirmar(token, totpReal.calcularCodigo(secreto), null));
    }

    @Test
    @DisplayName("un desafio caducado no vale")
    void unDesafioCaducadoNoVale() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        enrolado(secreto);
String token = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        desafios.get(token).setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        assertThrows(BadRequestException.class,
                () -> mfa.confirmar(token, totpReal.calcularCodigo(secreto), null));
    }

    @Test
    @DisplayName("con los dos campos a la vez, se rechaza el intento")
    void codigoYRecuperacionJuntosNoValen() {
        // Un codigo de recuperacion ya quemado en el mismo intento que un codigo
        // valido serviria para algo.
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        enrolado(secreto);
        String desafio = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);

        assertThrows(BadRequestException.class, () -> mfa.confirmar(
                desafio, totpReal.calcularCodigo(secreto), "ABCDE-FGHIJ"));
    }

    @Test
    @DisplayName("un codigo de recuperacion quemado no sirve la segunda vez")
    void elCodigoDeRecuperacionEsDeUnSoloUso() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        enrolado(secreto);
        String codigoRec = "ABCD3-FGHI5";
        MfaRecoveryCode almacenado = new MfaRecoveryCode();
        almacenado.setUser(usuario);
        // El servicio normaliza (quita guiones y espacios) ANTES de comparar, así que
        // el hash almacenado es del texto normalizado, no del que se teclea.
        almacenado.setCodeHash(mfaHash("ABCD3FGHI5"));
        almacenado.setCreadoAtMfa(OffsetDateTime.now());
        org.mockito.Mockito.when(recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(id(50)))
                .thenReturn(List.of(almacenado));

String primerIntento = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        mfa.confirmar(primerIntento, null, codigoRec);
        assertNotNull(almacenado.getUsadoAt(), "el codigo queda marcado como usado");

        // Segundo intento con el mismo: el almacenado ya esta usado, asi que la
        // consulta de disponibles no lo devuelve.
        org.mockito.Mockito.when(recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(id(50)))
                .thenReturn(List.of());
String segundoIntento = crearDesafio(MfaChallenge.PROPOSITO_LOGIN);
        assertThrows(BadRequestException.class, () -> mfa.confirmar(segundoIntento, null, codigoRec));
    }

    // ============================ desactivar ============================

    @Test
    @DisplayName("desactivar exige password Y codigo: con uno solo no basta")
    void desactivarExigeLasDosPruebas() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
UserMfa m = enrolado(secreto);

        // Solo la contraseña: con esto ya podría dejar la cuenta en un factor.
        assertThrows(BadRequestException.class,
                () -> mfa.desactivar(usuario, "MiClaveActual123!", ""));
        // Solo el código: alguien que abrió una sesión en un equipo público.
        assertThrows(BadRequestException.class,
                () -> mfa.desactivar(usuario, "", totpReal.calcularCodigo(secreto)));
        // Las dos correctas: aquí sí.
        mfa.desactivar(usuario, "MiClaveActual123!", totpReal.calcularCodigo(secreto));
        // Se mira la misma instancia que se pasó al servicio: volver a llamar a
        // enrolar() re-declararía el mock y devolvería un objeto limpio.
        assertFalse(m.isEnrolled());
    }

    @Test
    @DisplayName("desactivar borra el secreto y los codigos de recuperacion")
    void desactivarBorraTodoLoSecreto() {
        // Si no se borraran, un token robado serviría para recuperar el acceso a
        // la app de una cuenta que ya no lo tiene.
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = enrolado(secreto);
        MfaRecoveryCode r = new MfaRecoveryCode();
        r.setUser(usuario);
        org.mockito.Mockito.when(recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(id(50)))
                .thenReturn(List.of(r));

        mfa.desactivar(usuario, "MiClaveActual123!", totpReal.calcularCodigo(secreto));

        assertFalse(m.isEnrolled());
        assertNull(m.getSecretAlmacenado(), "el secreto cifrado debe desaparecer");
        org.mockito.Mockito.verify(recoveryCodeRepository).delete(r);
    }

    @Test
    @DisplayName("con la contraseña incorrecta, no se desactiva")
    void desactivarConPasswordErroneaNo() {
        mfaActivado();
        String secreto = totpReal.generarSecreto();
        UserMfa m = enrolado(secreto);

        assertThrows(BadRequestException.class,
                () -> mfa.desactivar(usuario, "NoEsLaClave123!", totpReal.calcularCodigo(secreto)));
        assertTrue(m.isEnrolled(), "la cuenta debe seguir con doble factor");
    }

    // ============================ utilidades ============================

    /**
     * Deja un desafío guardado como si el login lo hubiera emitido.
     *
     * <p>Devuelve el token EN CLARO, que es lo que el frontend tendría. El
     * servicio solo conoce su hash, así que el mock se monta sobre ese hash.
     */
    private String crearDesafio(String purpose) {
        String token = "token-de-prueba-" + purpose + "-" + System.nanoTime();
        MfaChallenge c = new MfaChallenge();
        c.setUser(usuario);
        c.setPurpose(purpose);
        c.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        c.setCreatedAt(OffsetDateTime.now());
        c.setTokenHash(mfaHash(token));
        desafios.put(token, c);
        org.mockito.Mockito.when(challengeRepository.findByTokenHash(mfaHash(token)))
                .thenReturn(Optional.of(c));
        org.mockito.Mockito.when(challengeRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        return token;
    }

    /** SHA-256 igual que el del servicio, para montar los fixtures. */
    private String mfaHash(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(
                    raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}