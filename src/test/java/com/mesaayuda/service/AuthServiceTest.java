package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.PasswordResetToken;
import com.mesaayuda.dto.AuthRequest;
import com.mesaayuda.dto.AuthResponse;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.LoginAttempt;
import com.mesaayuda.entity.RefreshToken;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.LoginAttemptRepository;
import com.mesaayuda.repository.RefreshTokenRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private com.mesaayuda.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private UserDetailsService userDetailsService;
    @Mock private SystemAuditService systemAuditService;
    @Mock private MicrosoftGraphUserSyncService microsoftGraphUserSyncService;
    @Mock private SessionConfigService sessionConfigService;
    @Mock private RateLimitService rateLimitService;

    @InjectMocks private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setNameRole("ADMIN");
        Area area = new Area();
        area.setNameArea("TI");
        user = new User();
        user.setIdUser(id(1));
        user.setCorporateEmailUser("admin@coacmagisterio.fin.ec");
        user.setPasswordUser("hash-viejo");

        // La duracion de la sesion sale de la configuracion, no del yml. Se deja
        // un valor fijo para que las pruebas no dependan de la base.
        lenient().when(sessionConfigService.duracion()).thenReturn(java.time.Duration.ofMinutes(15));
        lenient().when(sessionConfigService.warningMinutos()).thenReturn(5);
        lenient().when(sessionConfigService.autoRenew()).thenReturn(true);
        lenient().when(tokenProvider.segundosRestantes(anyString())).thenReturn(900L);
        user.setMustChangePassword(true);
        user.setRolUser(role);
        user.setArea(area);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changePassword_success_encodesAndClearsFlag() {
        when(userRepository.findById(id(1))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("vieja", "hash-viejo")).thenReturn(true);
        when(passwordEncoder.encode("nueva")).thenReturn("hash-nueva");

        authService.changePassword(id(1), "vieja", "nueva");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hash-nueva", captor.getValue().getPasswordUser());
        assertFalse(captor.getValue().getMustChangePassword());
        assertNull(captor.getValue().getTemporalPasswordExpiresAt());
        // El cambio corta también los tokens de acceso ya emitidos, no solo los de refresco.
        assertNotNull(captor.getValue().getTokensValidAfter());
        verify(refreshTokenRepository).revokeAllByUser(id(1));
    }

    @Test
    void logout_revocaElRefreshYCortaLosTokensDeAcceso() {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(id(5));
        rt.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(rt));

        authService.logout("token-en-claro");

        assertTrue(rt.getRevoked());
        verify(userRepository).invalidarTokensDeAcceso(eq(id(5)), any());
        verify(systemAuditService).record("LOGOUT", "User", id(5));
    }

    @Test
    void logout_conTokenDesconocidoNoTocaANadie() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("inventado");

        verify(userRepository, never()).invalidarTokensDeAcceso(any(java.util.UUID.class), any());
    }

    @Test
    void changePassword_wrongCurrent_throws() {
        when(userRepository.findById(id(1))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mal", "hash-viejo")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.changePassword(id(1), "mal", "nueva"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_samePassword_throws() {
        when(userRepository.findById(id(1))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("misma", "hash-viejo")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.changePassword(id(1), "misma", "misma"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userRepository.findById(id(99))).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> authService.changePassword(id(99), "x", "y"));
    }

    /**
     * El cambio de fondo de 1D.2: pedir el restablecimiento ya NO toca la
     * contraseña. Antes la generaba, la guardaba y la mandaba en el mismo
     * instante, así que cualquiera que conociera el correo de un compañero
     * (en este dominio son consecutivos) le tumbaba la sesión.
     */
@Test
    void forgotPassword_noCambiaLaContrasena_soloEmiteEnlace() {
        user.setStatusUser("ACTIVE");
        user.setDeletedUser(false);
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        // El fixture deja mustChangePassword en true, asi que lo que importa es
        // que forgotPassword no lo toque, no su valor absoluto.
        boolean mustAntes = user.getMustChangePassword();
        OffsetDateTime venceAntes = user.getTemporalPasswordExpiresAt();

        authService.forgotPassword(user.getCorporateEmailUser());

        // Lo importante: ni se encripta ni se guarda una contraseña nueva.
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        assertEquals(mustAntes, user.getMustChangePassword(),
                "no debe marcarse cambio pendiente todavia");
        assertEquals(venceAntes, user.getTemporalPasswordExpiresAt(),
                "no debe fijarse caducidad de contraseña temporal");

        // Y se emite un enlace de un solo uso para ese usuario.
        ArgumentCaptor<PasswordResetToken> enlace = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(enlace.capture());
        assertEquals(user, enlace.getValue().getUser());
        assertNotNull(enlace.getValue().getTokenHash());
        assertTrue(enlace.getValue().getExpiresAt().isAfter(OffsetDateTime.now()));

        // Lo guardado es el hash, no el token. Se comprueba contra el token real
        // que salió por correo: si se guardara en claro, leer la tabla bastaría
        // para resetsar la cuenta de cualquiera.
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyPasswordReset(eq(user), url.capture());
        String tokenCrudo = url.getValue().substring(url.getValue().indexOf("token=") + 6);
        assertNotEquals(tokenCrudo, enlace.getValue().getTokenHash());
        assertEquals(sha256De(tokenCrudo), enlace.getValue().getTokenHash());

        verify(passwordResetTokenRepository).invalidarPendientes(user.getIdUser());
    }

    /** Replica el hash que hace AuthService.hashToken, para poder compararlo. */
    private String sha256De(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** El correo lleva el enlace, no una contraseña que copiar. */
    @Test
    void forgotPassword_mandaElEnlaceYNoLaClave() {
        user.setStatusUser("ACTIVE");
        user.setDeletedUser(false);
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        authService.forgotPassword(user.getCorporateEmailUser());

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyPasswordReset(eq(user), url.capture());
        assertTrue(url.getValue().contains("token="),
                "debe ser un enlace con token: " + url.getValue());
        assertTrue(url.getValue().contains("/restablecer"));
    }

    /** Confirmar el enlace: cambia la clave, limpia la marca y tira las sesiones. */
    @Test
    void confirmarRestablecimiento_cambiaClaveYRevocaSesiones() {
        PasswordResetToken enlace = enlaceVivo(user);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(enlace));
        when(passwordEncoder.encode(anyString())).thenReturn("NUEVO_HASH");

        authService.confirmarRestablecimiento("token-de-prueba", "NuevaClave123!");

        assertEquals("NUEVO_HASH", user.getPasswordUser());
        assertFalse(user.getMustChangePassword());
        assertNull(user.getTemporalPasswordExpiresAt());
        assertTrue(enlace.isUsed(), "el enlace debe quedar consumido");
        // Lo esencial: un refresh token robado deja de servir.
        verify(refreshTokenRepository).revokeAllByUser(user.getIdUser());
    }

    /** Un enlace es un cambio. Reutilizarlo no debe volver a cambiar la clave. */
    @Test
    void confirmarRestablecimiento_enlaceYaUsado_seRechaza() {
        PasswordResetToken enlace = enlaceVivo(user);
        enlace.setUsed(true);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(enlace));

        assertThrows(BadRequestException.class,
                () -> authService.confirmarRestablecimiento("token", "NuevaClave123!"));
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
    }

    @Test
    void confirmarRestablecimiento_enlaceCaducado_seRechaza() {
        PasswordResetToken enlace = enlaceVivo(user);
        enlace.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(enlace));

        assertThrows(BadRequestException.class,
                () -> authService.confirmarRestablecimiento("token", "NuevaClave123!"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void confirmarRestablecimiento_tokenInventado_seRechaza() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> authService.confirmarRestablecimiento("inventado", "NuevaClave123!"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** Un enlace no puede resucitar una cuenta desactivada. */
    @Test
    void confirmarRestablecimiento_cuentaInactiva_seRechaza() {
        user.setStatusUser("INACTIVE");
        user.setDeletedUser(false);
        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(enlaceVivo(user)));

        assertThrows(BadRequestException.class,
                () -> authService.confirmarRestablecimiento("token", "NuevaClave123!"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    private PasswordResetToken enlaceVivo(User u) {
        PasswordResetToken enlace = new PasswordResetToken();
        enlace.setUser(u);
        enlace.setTokenHash("hash");
        enlace.setExpiresAt(OffsetDateTime.now().plusHours(1));
        enlace.setUsed(false);
        return enlace;
    }

    /**
     * Antes este caso lanzaba BadRequestException diciendo que no existía la
     * cuenta. Eso permetia enumerar los correos de la organización con una
     * simple diferencia en la respuesta, así que ahora devuelve en silencio y
     * el controlador responde siempre lo mismo.
     */
    @Test
    void forgotPassword_inactiveOrDeleted_noRespondeNiNotifica() {
        user.setStatusUser("INACTIVE");
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        authService.forgotPassword(user.getCorporateEmailUser());

        verify(notificationService, never()).notifyPasswordReset(any(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsNonCorporateDomain() {
        RegisterRequest req = new RegisterRequest("1723", "x@gmail.com", "Temporal123!",
                null, "USER", id(1), null);
        assertThrows(BadRequestException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success_returnsAuthResponse() {
        Authentication auth = authWithAuthority("ROLE_ADMIN");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(any(), anyLong())).thenReturn("TOKEN");
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        AuthResponse res = authService.login(new AuthRequest(user.getCorporateEmailUser(), "cosa"));

        assertEquals("TOKEN", res.token());
        assertEquals(user.getIdUser(), res.userId());
        assertEquals("ADMIN", res.role());
        assertEquals("TI", res.area());
        assertEquals(List.of("ROLE_ADMIN"), res.permissions());
        assertTrue(res.mustChangePassword());
    }

    @Test
    void login_temporalPasswordExpired_forcesChange() {
        Authentication auth = authWithAuthority("ROLE_ADMIN");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(any(), anyLong())).thenReturn("TOKEN");
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().minusHours(1));
        user.setMustChangePassword(false);
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        AuthResponse res = authService.login(new AuthRequest(user.getCorporateEmailUser(), "cosa"));

        assertTrue(res.mustChangePassword());
        verify(userRepository).save(user);
    }

    @Test
    void login_temporalPasswordStillValid_doesNotForceChange() {
        Authentication auth = authWithAuthority("ROLE_ADMIN");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(any(), anyLong())).thenReturn("TOKEN");
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(60));
        user.setMustChangePassword(false);
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        AuthResponse res = authService.login(new AuthRequest(user.getCorporateEmailUser(), "cosa"));

        assertFalse(res.mustChangePassword());
        verify(userRepository, never()).save(any());
    }

@Test
      void login_registraElFalloEnCadaIntentoFallido() {
          // El contador y el bloqueo viven en LoginAttemptService, en su propia
          // transaccion. Aqui solo se comprueba que AuthService avise cada vez.
          when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                  .thenThrow(BadCredentialsException.class);
          lenient().when(loginAttemptService.buscar(anyString())).thenReturn(null);

          for (int i = 0; i < 5; i++) {
              assertThrows(BadCredentialsException.class,
                      () -> authService.login(new AuthRequest(user.getCorporateEmailUser(), "mal")));
          }

          verify(loginAttemptService, times(5)).registrarFallo(user.getCorporateEmailUser());
      }

      @Test
      void login_blockedWhileLocked() {
          LoginAttempt locked = new LoginAttempt();
          locked.setUserKey(user.getCorporateEmailUser());
          locked.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
          when(loginAttemptService.buscar(user.getCorporateEmailUser())).thenReturn(locked);
          when(loginAttemptService.estaBloqueada(locked)).thenReturn(true);
          when(loginAttemptService.minutosRestantes(locked)).thenReturn(10L);

          BadRequestException ex = assertThrows(BadRequestException.class,
                  () -> authService.login(new AuthRequest(user.getCorporateEmailUser(), "x")));
          assertTrue(ex.getMessage().contains("minuto"));
          verify(authenticationManager, never()).authenticate(any());
          // Con la cuenta bloqueada no se llega a comprobar la contraseña.
          verify(loginAttemptService, never()).registrarFallo(anyString());
      }

      @Test
      void login_success_clearsAttempts() {
          Authentication auth = authWithAuthority("ROLE_ADMIN");
          when(loginAttemptService.buscar(user.getCorporateEmailUser())).thenReturn(null);
          when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
          when(tokenProvider.generateToken(any(), anyLong())).thenReturn("TOKEN");
          when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

          authService.login(new AuthRequest(user.getCorporateEmailUser(), "correcta"));

          verify(loginAttemptService).limpiar(user.getCorporateEmailUser());
      }

    @Test
    void refresh_rotatesAndReturnsNewPair() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(id(1));
        stored.setTokenHash("hash-viejo");
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(id(1))).thenReturn(Optional.of(user));

        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getCorporateEmailUser(), "pw", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userDetailsService.loadUserByUsername(user.getCorporateEmailUser())).thenReturn(details);
        when(tokenProvider.generateToken(any(), anyLong())).thenReturn("NEW-ACCESS");

        AuthResponse res = authService.refresh("token-viejo");

        assertEquals("NEW-ACCESS", res.token());
        assertEquals(user.getIdUser(), res.userId());
        assertEquals("ADMIN", res.role());
        assertNotNull(res.refreshToken());
        assertTrue(stored.getRevoked(), "el token viejo debe revocarse (rotación)");
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

@Test
    void refresh_revoked_throws() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(id(1));
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        stored.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
  
        assertThrows(BadRequestException.class, () -> authService.refresh("token"));
    }

    /**
     * La rotación normal encadena el token viejo con el nuevo.
     *
     * <p>Sin ese encadenamiento no se puede detectar el robo: al reutilizar un
     * token viejo solo se vería que está revocado, igual que un token inventado.
     */
    @Test
    void refresh_rotaYEncadenaElTokenViejoConElNuevo() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(id(1));
        stored.setTokenHash("hash-viejo");
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(id(1))).thenReturn(Optional.of(user));
        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getCorporateEmailUser(), "pw", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userDetailsService.loadUserByUsername(user.getCorporateEmailUser())).thenReturn(details);
        when(tokenProvider.generateToken(any(), anyLong())).thenReturn("ACCESO");

        AuthResponse res = authService.refresh("token-viejo");

        assertTrue(stored.getRevoked());
        assertNotNull(stored.getReplacedByHash(), "el viejo debe apuntar al sustituto");
        assertNotEquals(stored.getTokenHash(), stored.getReplacedByHash(),
                "el sustituto no puede ser el mismo token");
        assertNull(stored.getReuseDetectedAt(),
                "una rotación normal no es reutilización: no debe marcarse");
        assertNotNull(res.refreshToken());
    }

    /**
     * Reutilizar un token ya rotado significa que hay dos manos con el mismo
     * token: el titular ya tiene el par nuevo. Se cierran todas las sesiones.
     */
    @Test
    void refresh_tokenRobado_reutilizado_revocaTodasLasSesiones() {
        RefreshToken robado = new RefreshToken();
        robado.setUserId(id(1));
        robado.setTokenHash("hash-robado");
        robado.setExpiresAt(OffsetDateTime.now().plusDays(1));
        robado.setRevoked(true);
        robado.setReplacedByHash("hash-del-sustituto");
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(robado));

        assertThrows(BadRequestException.class, () -> authService.refresh("token-robado"));

        verify(refreshTokenRepository).revokeAllByUser(id(1));
        assertNotNull(robado.getReuseDetectedAt(), "debe constar cuándo se detectó");
        verify(systemAuditService).record(eq("REFRESH_TOKEN_REUSE"), eq("User"), eq(id(1)),
                eq(null), anyString());
    }

    /**
     * El ladrón va a seguir insistiendo con el mismo token. Cada intento debe
     * seguir rechazándose, pero sin ensuciar el log con una línea por intento.
     */
    @Test
    void refresh_reutilizadoVariasVeces_rechazaSiemprePeroAvisaUna() {
        RefreshToken robado = new RefreshToken();
        robado.setUserId(id(1));
        robado.setExpiresAt(OffsetDateTime.now().plusDays(1));
        robado.setRevoked(true);
        robado.setReuseDetectedAt(OffsetDateTime.now());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(robado));

assertThrows(BadRequestException.class, () -> authService.refresh("token"));
        assertThrows(BadRequestException.class, () -> authService.refresh("token"));
  
        // Sin nuevo aviso, que es lo que se queria evitar. Las sesiones ya estaban
        // revocadas por la deteccion anterior, asi que no hay nada que repetir.
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
        verify(systemAuditService, never()).record(eq("REFRESH_TOKEN_REUSE"), any(), any(), any(), anyString());
        verify(systemAuditService, never()).record(eq("REFRESH_TOKEN_REUSE"), any(), any(), anyString());
    }

    /** Un token inexistente se rechaza sin tocar sesiones ajenas. */
    @Test
    void refresh_tokenInexistente_noDisparaLaDeteccion() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> authService.refresh("inventado"));

        verify(refreshTokenRepository, never()).revokeAllByUser(any());
verify(systemAuditService, never()).record(eq("REFRESH_TOKEN_REUSE"), any(), any(), any(), anyString());
        verify(systemAuditService, never()).record(eq("REFRESH_TOKEN_REUSE"), any(), any(), anyString());
    }

    @Test
    void refresh_expired_throws() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(id(1));
        stored.setExpiresAt(OffsetDateTime.now().minusDays(1));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(BadRequestException.class, () -> authService.refresh("token"));
    }

    private Authentication authWithAuthority(String authority) {
        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenAnswer(inv -> List.of((GrantedAuthority) () -> authority));
        return auth;
    }
}