package com.mesaayuda.service;

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
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserDetailsService userDetailsService;
    @Mock private SystemAuditService systemAuditService;

    @InjectMocks private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setNameRole("ADMIN");
        Area area = new Area();
        area.setNameArea("TI");
        user = new User();
        user.setIdUser(1L);
        user.setCorporateEmailUser("admin@coacmagisterio.fin.ec");
        user.setPasswordUser("hash-viejo");
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("vieja", "hash-viejo")).thenReturn(true);
        when(passwordEncoder.encode("nueva")).thenReturn("hash-nueva");

        authService.changePassword(1L, "vieja", "nueva");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hash-nueva", captor.getValue().getPasswordUser());
        assertFalse(captor.getValue().getMustChangePassword());
        assertNull(captor.getValue().getTemporalPasswordExpiresAt());
    }

    @Test
    void changePassword_wrongCurrent_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mal", "hash-viejo")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.changePassword(1L, "mal", "nueva"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_samePassword_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("misma", "hash-viejo")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.changePassword(1L, "misma", "misma"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> authService.changePassword(99L, "x", "y"));
    }

    @Test
    void forgotPassword_generatesRandomAndNotifies() {
        user.setStatusUser("ACTIVE");
        user.setDeletedUser(false);
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        when(passwordEncoder.encode(encoded.capture())).thenAnswer(inv -> "HASH");

        authService.forgotPassword(user.getCorporateEmailUser());

        ArgumentCaptor<String> tempPwd = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyPasswordReset(eq(user), tempPwd.capture());

        String raw = tempPwd.getValue();
        assertNotNull(raw);
        assertTrue(raw.length() >= 8);
        assertEquals(raw, encoded.getValue(), "la clave enviada debe ser la misma que se encripta");
        verify(userRepository).save(user);
        assertTrue(user.getMustChangePassword());
        assertNotNull(user.getTemporalPasswordExpiresAt());
        assertTrue(user.getTemporalPasswordExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    void forgotPassword_inactiveOrDeleted_throws() {
        user.setStatusUser("INACTIVE");
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class, () -> authService.forgotPassword(user.getCorporateEmailUser()));
    }

    @Test
    void register_rejectsNonCorporateDomain() {
        RegisterRequest req = new RegisterRequest("1723", "x@gmail.com", "Temporal123!",
                null, "USER", 1L);
        assertThrows(BadRequestException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success_returnsAuthResponse() {
        Authentication auth = authWithAuthority("ROLE_ADMIN");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(any())).thenReturn("TOKEN");
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
        when(tokenProvider.generateToken(any())).thenReturn("TOKEN");
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
        when(tokenProvider.generateToken(any())).thenReturn("TOKEN");
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(60));
        user.setMustChangePassword(false);
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        AuthResponse res = authService.login(new AuthRequest(user.getCorporateEmailUser(), "cosa"));

        assertFalse(res.mustChangePassword());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_locksAfterMaxFailures() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(BadCredentialsException.class);
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUserKey(user.getCorporateEmailUser());
        when(loginAttemptRepository.findByUserKey(user.getCorporateEmailUser())).thenAnswer(inv -> Optional.of(attempt));

        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.login(new AuthRequest(user.getCorporateEmailUser(), "mal")));
        }

        assertEquals(0, attempt.getFailedCount());
        assertNotNull(attempt.getLockedUntil());
        assertTrue(attempt.getLockedUntil().isAfter(OffsetDateTime.now()));
        verify(loginAttemptRepository, times(5)).save(attempt);
    }

    @Test
    void login_blockedWhileLocked() {
        LoginAttempt locked = new LoginAttempt();
        locked.setUserKey(user.getCorporateEmailUser());
        locked.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        when(loginAttemptRepository.findByUserKey(user.getCorporateEmailUser())).thenReturn(Optional.of(locked));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.login(new AuthRequest(user.getCorporateEmailUser(), "x")));
        assertTrue(ex.getMessage().contains("minuto"));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_success_clearsAttempts() {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUserKey(user.getCorporateEmailUser());
        attempt.setFailedCount(3);
        when(loginAttemptRepository.findByUserKey(user.getCorporateEmailUser())).thenReturn(Optional.of(attempt));

        Authentication auth = authWithAuthority("ROLE_ADMIN");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(any())).thenReturn("TOKEN");
        when(userRepository.findByCorporateEmailUser(user.getCorporateEmailUser())).thenReturn(Optional.of(user));

        authService.login(new AuthRequest(user.getCorporateEmailUser(), "correcta"));

        verify(loginAttemptRepository).delete(attempt);
        verify(loginAttemptRepository, never()).save(any());
    }

    @Test
    void refresh_rotatesAndReturnsNewPair() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
        stored.setTokenHash("hash-viejo");
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getCorporateEmailUser(), "pw", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userDetailsService.loadUserByUsername(user.getCorporateEmailUser())).thenReturn(details);
        when(tokenProvider.generateToken(any())).thenReturn("NEW-ACCESS");

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
        stored.setUserId(1L);
        stored.setExpiresAt(OffsetDateTime.now().plusDays(1));
        stored.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(BadRequestException.class, () -> authService.refresh("token"));
    }

    @Test
    void refresh_expired_throws() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
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