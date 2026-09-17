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
import com.mesaayuda.util.PasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private static final String CORPORATE_DOMAIN = "@coacmagisterio.fin.ec";
    private static final long TEMPORAL_PASSWORD_MINUTES = 24 * 60;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String key = request.email() == null ? "" : request.email().trim().toLowerCase();
        LoginAttempt attempt = loginAttemptRepository.findByUserKey(key).orElse(null);

        if (isLocked(attempt)) {
            long minutes = Duration.between(OffsetDateTime.now(), attempt.getLockedUntil()).toMinutes() + 1;
            throw new BadRequestException("Demasiados intentos fallidos. Intente nuevamente en " + minutes + " minuto(s)");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            recordFailedAttempt(key, attempt);
            systemAuditService.record("LOGIN_FAILED", "User", null, key);
            throw e;
        }
        clearAttempts(attempt);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByCorporateEmailUser(request.email())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        if (isTemporalPasswordExpired(user)) {
            user.setMustChangePassword(true);
            userRepository.save(user);
        }

        systemAuditService.record("LOGIN", "User", user.getIdUser());
        return buildAuthResponse(user, tokenProvider.generateToken(authentication),
                issueRefreshToken(user), authorities());
    }

    public AuthResponse register(RegisterRequest request) {
        if (!request.corporateEmail().toLowerCase().endsWith(CORPORATE_DOMAIN)) {
            throw new BadRequestException("Solo se permiten correos corporativos del dominio " + CORPORATE_DOMAIN);
        }
        if (userRepository.existsByCorporateEmailUser(request.corporateEmail())) {
            throw new BadRequestException("El correo corporativo ya está registrado");
        }
        if (userRepository.existsByIdentificationUser(request.identification())) {
            throw new BadRequestException("La identificación ya está registrada");
        }

        Role role = roleRepository.findByNameRole(request.roleName().toUpperCase())
                .filter(r -> !r.getDeletedRole())
                .orElseThrow(() -> new BadRequestException("Rol inválido: " + request.roleName()));
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));

        if (!role.getNameRole().equals("USER")) {
            throw new BadRequestException("Solo se permite auto-registro con rol USER");
        }

        User user = new User();
        user.setIdentificationUser(request.identification());
        user.setCorporateEmailUser(request.corporateEmail());
        user.setPasswordUser(passwordEncoder.encode(request.password()));
        user.setPhoneUser(request.phone());
        user.setStatusUser("ACTIVE");
        user.setRolUser(role);
        user.setArea(area);
        user.setDeletedUser(false);
        user.setMustChangePassword(true);
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        user = userRepository.save(user);

        log.info("Nuevo usuario registrado: {}", user.getCorporateEmailUser());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.corporateEmail(), request.password()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return buildAuthResponse(user, tokenProvider.generateToken(authentication),
                issueRefreshToken(user), authorities());
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordUser())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        if (currentPassword.equals(newPassword)) {
            throw new BadRequestException("La nueva contraseña debe ser diferente a la actual");
        }

        user.setPasswordUser(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTemporalPasswordExpiresAt(null);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(userId);
        systemAuditService.record("PASSWORD_CHANGE", "User", userId);
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        User user = userRepository.findByCorporateEmailUser(normalized)
                .filter(u -> !u.getDeletedUser() && "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                .orElseThrow(() -> new BadRequestException("No existe una cuenta activa con ese correo"));

        String tempPassword = PasswordGenerator.generate();
        user.setPasswordUser(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        userRepository.save(user);

        systemAuditService.record("PASSWORD_RESET", "User", user.getIdUser(), normalized);
        notificationService.notifyPasswordReset(user, tempPassword);
        log.info("Contraseña restablecida para {}", normalized);
    }

    private boolean isTemporalPasswordExpired(User user) {
        return user.getTemporalPasswordExpiresAt() != null
                && user.getTemporalPasswordExpiresAt().isBefore(OffsetDateTime.now());
    }

    private boolean isLocked(LoginAttempt attempt) {
        return attempt != null && attempt.getLockedUntil() != null
                && attempt.getLockedUntil().isAfter(OffsetDateTime.now());
    }

    private void recordFailedAttempt(String key, LoginAttempt attempt) {
        if (attempt == null) {
            attempt = new LoginAttempt();
            attempt.setUserKey(key);
            attempt.setFailedCount(0);
        }
        attempt.setFailedCount(attempt.getFailedCount() + 1);
        if (attempt.getFailedCount() >= MAX_LOGIN_ATTEMPTS) {
            attempt.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_MINUTES));
            attempt.setFailedCount(0);
        }
        loginAttemptRepository.save(attempt);
    }

    private void clearAttempts(LoginAttempt attempt) {
        if (attempt != null) {
            loginAttemptRepository.delete(attempt);
        }
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        String hash = hashToken(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .filter(rt -> !rt.getRevoked())
                .orElseThrow(() -> new BadRequestException("Refresh token inválido o revocado"));

        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Refresh token expirado");
        }

        User user = userRepository.findById(stored.getUserId())
                .filter(u -> !u.getDeletedUser() && "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildAuthResponse(user, buildAccessTokenFor(user),
                issueRefreshToken(user), accessAuthorities(user));
    }

    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    private String buildAccessTokenFor(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getCorporateEmailUser());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        return tokenProvider.generateToken(authentication);
    }

    private List<String> accessAuthorities(User user) {
        return userDetailsService.loadUserByUsername(user.getCorporateEmailUser())
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private String issueRefreshToken(User user) {
        String raw = UUID.randomUUID() + "." + System.nanoTime();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getIdUser());
        rt.setTokenHash(hashToken(raw));
        rt.setExpiresAt(OffsetDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);
        return raw;
    }

    private String hashToken(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private List<String> authorities() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private AuthResponse buildAuthResponse(User user, String token, String refreshToken, List<String> authorities) {
        return new AuthResponse(
                token,
                "Bearer",
                user.getIdUser(),
                user.getCorporateEmailUser(),
                user.getRolUser().getNameRole(),
                user.getArea().getNameArea(),
                authorities,
                user.getMustChangePassword(),
                refreshToken);
    }
}