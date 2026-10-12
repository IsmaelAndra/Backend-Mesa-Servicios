package com.mesaayuda.service;

import com.mesaayuda.dto.AuthRequest;
import com.mesaayuda.dto.AuthResponse;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.LoginAttempt;
import com.mesaayuda.entity.PasswordResetToken;
import com.mesaayuda.entity.RefreshToken;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.PasswordResetTokenRepository;
import com.mesaayuda.repository.RefreshTokenRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.JwtTokenProvider;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private static final String CORPORATE_DOMAIN = "@coacmagisterio.fin.ec";
    private static final long TEMPORAL_PASSWORD_MINUTES = 24 * 60;

    /**
     * Ventana del enlace de restablecimiento. Larga a propósito: el correo puede
     * caer en una bandeja que alguien revisa una vez al día. Por eso el enlace es
     * de un solo uso y pedir uno nuevo invalida el anterior.
     */
    private static final long RESET_TOKEN_MINUTES = 60 * 24;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private SessionConfigService sessionConfigService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AreaRepository areaRepository;
    @Autowired private SucursalRepository sucursalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private NotificationService notificationService;


    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Solo para comprobar, tras confirmar el MFA, que el desafío quedó consumido.
     * Un estado en memoria no serviría: con dos instancias del backend, la que
     * confirmara el código podría no ser la que emite la sesión.
     */
    @Autowired
    private com.mesaayuda.repository.MfaChallengeRepository mfaChallengeRepository;

    /**
     * Origen del frontend para los enlaces del correo. Misma propiedad que usa
     * NotificationService, para que los dos compongan la URL igual.
     */
    @Value("${app.frontend-base-url:}")
    private String frontendUrl;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private MicrosoftGraphUserSyncService microsoftGraphUserSyncService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Transactional
public AuthResponse login(AuthRequest request) {
          String key = request.email() == null ? "" : request.email().trim().toLowerCase();
          LoginAttempt attempt = loginAttemptService.buscar(key);

          if (loginAttemptService.estaBloqueada(attempt)) {
              long minutes = loginAttemptService.minutosRestantes(attempt);
              systemAuditService.record("LOGIN_BLOCKED", "User", null, key);
              throw new BadRequestException("Demasiados intentos fallidos. Intente nuevamente en " + minutes + " minuto(s)");
          }
          String origen = origenActual();
          rateLimitService.exigir("login-origen", origen,
                  RateLimitService.MAX_LOGIN_ORIGEN, RateLimitService.VENTANA_LOGIN_ORIGEN);

          Authentication authentication;
          try {
              authentication = authenticationManager.authenticate(
                      new UsernamePasswordAuthenticationToken(request.email(), request.password()));
          } catch (BadCredentialsException e) {
              // Se registra en su propia transaccion: el badRequest de aqui
              // revierte la de login(), y si el save compartiera transaccion el
              // contador se perderia y el bloqueo no existiria.
              loginAttemptService.registrarFallo(key);
              rateLimitService.registrarAparte("login-origen", origen,
                      RateLimitService.MAX_LOGIN_ORIGEN, RateLimitService.VENTANA_LOGIN_ORIGEN);
              systemAuditService.record("LOGIN_FAILED", "User", null, key);
              throw e;
          }
          loginAttemptService.limpiar(key);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByCorporateEmailUser(request.email())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        if (isTemporalPasswordExpired(user)) {
            user.setMustChangePassword(true);
            userRepository.save(user);
        }

        systemAuditService.record("LOGIN", "User", user.getIdUser());
        // Sincroniza datos de Microsoft 365 en segundo plano (async, no bloqueante).
        microsoftGraphUserSyncService.syncOnLogin(user.getIdUser());
        return buildAuthResponse(user,
                tokenProvider.generateToken(authentication, sessionConfigService.duracion().toMillis()),
                issueRefreshToken(user), authorities());
    }

    /**
     * Sede de la persona. Opcional a proposito: si no se elige, o si la sede ya
     * no existe, la cuenta se queda sin sede en vez de fallar el alta.
     */
    private Sucursal resolverSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return null;
        }
        return sucursalRepository.findById(sucursalId)
                .filter(s -> !s.getDeletedSucursal())
                .orElse(null);
    }
    public AuthResponse register(RegisterRequest request) {
        // Cada alta cuenta, salga bien o mal: el abuso de un formulario público
        // de registro es crear cuentas, no fallar al crearlas.
        String origen = origenActual();
        rateLimitService.exigir("registro-origen", origen,
                RateLimitService.MAX_REGISTRO_ORIGEN, RateLimitService.VENTANA_REGISTRO_ORIGEN);
        rateLimitService.registrarAparte("registro-origen", origen,
                RateLimitService.MAX_REGISTRO_ORIGEN, RateLimitService.VENTANA_REGISTRO_ORIGEN);
        if (!request.corporateEmail().toLowerCase().endsWith(CORPORATE_DOMAIN)) {
            throw new BadRequestException("Solo se permiten correos corporativos del dominio " + CORPORATE_DOMAIN);
        }
        if (request.identification() == null || request.identification().isBlank()) {
            throw new BadRequestException("La identificación es obligatoria en el auto-registro");
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
        user.setSucursal(resolverSucursal(request.sucursalId()));
        user.setDeletedUser(false);
        user.setMustChangePassword(true);
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        user = userRepository.save(user);

        log.info("Nuevo usuario registrado: {}", user.getCorporateEmailUser());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.corporateEmail(), request.password()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return buildAuthResponse(user,
                tokenProvider.generateToken(authentication, sessionConfigService.duracion().toMillis()),
                issueRefreshToken(user), authorities());
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
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
        // Corta también los tokens de acceso ya emitidos: revocar solo los de
        // refresco dejaba a quien tuviera uno 15 minutos más dentro.
        user.setTokensValidAfter(OffsetDateTime.now());
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(userId);
        systemAuditService.record("PASSWORD_CHANGE", "User", userId);
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();

        // Límite por objetivo y por origen: sin esto se podía pedir el
        // reinicio de una cuenta conocida tantas veces como se quisiera, y cada
        // intento enviaba un correo.
        String origen = origenActual();
        rateLimitService.preparar("forgot-password", normalized);
        rateLimitService.exigir("forgot-password", normalized,
                RateLimitService.MAX_RESTABLECER, RateLimitService.VENTANA_RESTABLECER);
        rateLimitService.exigir("forgot-password-origen", origen,
                RateLimitService.MAX_RESTABLECER * 4, RateLimitService.VENTANA_RESTABLECER);

        User user = userRepository.findByCorporateEmailUser(normalized)
                .filter(u -> !u.getDeletedUser() && "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                .orElse(null);

        if (user == null) {
            // Antes respondía "No existe una cuenta activa con ese correo", lo
            // que permitía enumerar los correos de la organización. Se cuenta el
            // intento igual y se responde siempre lo mismo.
            rateLimitService.registrar("forgot-password", normalized,
                    RateLimitService.MAX_RESTABLECER, RateLimitService.VENTANA_RESTABLECER);
            log.info("Petición de restablecimiento para un correo sin cuenta activa ({})", origen);
            return;
        }

rateLimitService.registrar("forgot-password", normalized,
                  RateLimitService.MAX_RESTABLECER, RateLimitService.VENTANA_RESTABLECER);
          rateLimitService.registrar("forgot-password-origen", origen,
                  RateLimitService.MAX_RESTABLECER * 4, RateLimitService.VENTANA_RESTABLECER);

          // Aquí es donde cambia el comportamiento: antes se generaba una
          // contraseña temporal, se guardaba y se mandaba por correo en la misma
          // petición. Eso no probaba nada. Bastaba con conocer el correo de un
          // compañero (en este dominio son consecutivos y se deducen) para
          // tumbarle la sesión y forzarle un cambio.
          //
          // Ahora la petición solo emite un enlace. La contraseña no se toca
          // hasta que el titular lo abre, y al abrirlo se revocan todas sus
          // sesiones: si el motivo del restablecimiento es que sospechan de un
          // robo, dejar el refresh token vivo sería no hacer nada.
          passwordResetTokenRepository.invalidarPendientes(user.getIdUser());

          String token = UUID.randomUUID() + "." + UUID.randomUUID();
          PasswordResetToken enlace = new PasswordResetToken();
          enlace.setUser(user);
          enlace.setTokenHash(hashToken(token));
          enlace.setExpiresAt(OffsetDateTime.now().plusMinutes(RESET_TOKEN_MINUTES));
          enlace.setCreatedAt(OffsetDateTime.now());
          passwordResetTokenRepository.save(enlace);

          String urlReset = urlFrontend("/restablecer") + "?token=" + token;
          systemAuditService.record("PASSWORD_RESET_REQUESTED", "User",
                  user.getIdUser(), normalized);
          notificationService.notifyPasswordReset(user, urlReset);
          log.info("Enlace de restablecimiento emitido para {}", normalized);
      }

      /**
       * Segunda mitad del restablecimiento: el titular abre el enlace y fija su
       * contraseña.
       *
       * <p>Un token = un cambio. El enlace se marca usado en la misma
       * transacción que cambia la contraseña, de modo que un doble clic o un
       * reenvío del correo no la deja pasar dos veces.
       */
      @Transactional
      public void confirmarRestablecimiento(String token, String nuevaPassword) {
          String limpio = token == null ? "" : token.trim();
          if (limpio.isEmpty()) {
              throw new BadRequestException("El enlace de restablecimiento no es valido o ya expiro.");
          }

          PasswordResetToken enlace = passwordResetTokenRepository
                  .findByTokenHash(hashToken(limpio))
                  .orElseThrow(() -> new BadRequestException(
                          "El enlace de restablecimiento no es valido o ya expiro."));

          if (enlace.isUsed() || enlace.getExpiresAt().isBefore(OffsetDateTime.now())) {
              throw new BadRequestException("El enlace de restablecimiento no es valido o ya expiro.");
          }

          User user = enlace.getUser();
          if (user == null || Boolean.TRUE.equals(user.getDeletedUser())
                  || !"ACTIVE".equalsIgnoreCase(user.getStatusUser())) {
              throw new BadRequestException("El enlace de restablecimiento no es valido o ya expiro.");
          }

          // Las reglas de longitud las pone el DTO (ResetPasswordRequest), igual
          // que en changePassword. Aquí solo se comprueba que la cuenta siga
          // viva, porque un enlace puede quedar abierto semanas en un móvil.

          user.setPasswordUser(passwordEncoder.encode(nuevaPassword));
          user.setMustChangePassword(false);
          user.setTemporalPasswordExpiresAt(null);
          user.setTokensValidAfter(OffsetDateTime.now());
          userRepository.save(user);

          enlace.setUsed(true);
          passwordResetTokenRepository.save(enlace);

          // Sesiones por el suelo. El resto ya lo hace changePassword; aquí es
          // igual de importante porque el robo de un refresh token es el motivo
          // habitual de pedir un restablecimiento.
          refreshTokenRepository.revokeAllByUser(user.getIdUser());

          systemAuditService.record("PASSWORD_RESET", "User", user.getIdUser(),
                  user.getCorporateEmailUser());
          log.info("Contraseña restablecida por enlace para {}",
                  user.getCorporateEmailUser());
      }

      /** Origen del frontend para los enlaces del correo. */
      private String urlFrontend(String ruta) {
          String base = frontendUrl == null ? "" : frontendUrl.trim();
          while (base.endsWith("/")) {
              base = base.substring(0, base.length() - 1);
          }
          return base + ruta;
      }

    /**
     * IP de origen para los límites, leída del atributo que deja ClientIpFilter.
     *
     * <p>Antes se leía {@code X-Forwarded-For} directamente y se cogía el primer
     * valor. Eso era un agujero: un atacante podía mandar esa cabecera con un
     * valor distinto en cada intento y cada uno parecía venir de otra IP, con lo
     * que el límite por origen no limitaba nada. El filtro decide una sola vez,
     * según si hay proxy declarado, y aquí solo se lee lo que resolvió.
     */
    private String origenActual() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "desconocido";
        }
        Object ip = request.getAttribute(com.mesaayuda.security.ClientIpFilter.ATRIBUTO_IP);
        if (ip != null && !ip.toString().isBlank()) {
            return ip.toString();
        }
        return request.getRemoteAddr() == null ? "desconocido" : request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes =
                (RequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        ServletRequestAttributes servlet = (ServletRequestAttributes) attributes;
        return servlet.getRequest();
    }

    private boolean isTemporalPasswordExpired(User user) {
        return user.getTemporalPasswordExpiresAt() != null
                && user.getTemporalPasswordExpiresAt().isBefore(OffsetDateTime.now());
    }

/**
     * Usuario por correo, para el paso de MFA del login.
     *
     * <p>Se busca DESPUÉS de que la contraseña haya resultado correcta, que es
     * cuando el login llama a este método. Por eso no filtra nada: quien llega
     * aquí ya superar la contraseña.
     */
    @Transactional(readOnly = true)
    public User buscarPorCorreo(String email) {
        String clave = email == null ? "" : email.trim().toLowerCase();
        return userRepository.findByCorporateEmailUser(clave)
                .filter(u -> !u.getDeletedUser() && "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                .orElse(null);
    }

    /**
     * Emite la sesión una vez confirmado el segundo factor.
     *
     * <p>Se separa de {@link #refresh} a propósito. Al confirmar un desafío hay
     * que volver a emitir un par nuevo, y reutilizar el camino del refresh
     * obligaría a fabricar un refresh token solo para poder rotarlo, con la
     * detección de reutilización pendiente de un token que nunca existió.
     *
     * @param tokenDesafio solo se usa para localizar al usuario que confirmó
     */
    @Transactional
    public AuthResponse sesionTrasMfa(String tokenDesafio) {
        User user = userPorDesafioConfirmado(tokenDesafio);
        return buildAuthResponse(user, buildAccessTokenFor(user),
                issueRefreshToken(user), accessAuthorities(user));
    }

    @Transactional
    public AuthResponse sesionTrasMfa(User user) {
        return buildAuthResponse(user, buildAccessTokenFor(user),
                issueRefreshToken(user), accessAuthorities(user));
    }

    /**
     * Usuario del desafío recién validado.
     *
     * <p>Se vuelve a consultar el desafío, ya consumido, porque MfaService acaba de
     * marcarlo como usado: leerlo por el hash y comprobar que figura usado es lo
     * que impide reutilizar un desafío viejo para abrir una sesión nueva.
     */
    private User userPorDesafioConfirmado(String tokenDesafio) {
        if (tokenDesafio == null || tokenDesafio.isBlank()) {
            throw new BadRequestException("El desafío no es válido.");
        }
        com.mesaayuda.entity.MfaChallenge desafio =
                mfaChallengeRepository.findByTokenHash(hashToken(tokenDesafio.trim()))
                        .orElseThrow(() -> new BadRequestException("El desafío no es válido."));
        if (desafio.getUsedAt() == null) {
            // MfaService.confirmar() lo consume antes de llegar aquí. Si no está
            // usado, alguien está llamando a este endpoint sin haber pasado por
            // la verificación del código.
            throw new BadRequestException("El segundo factor todavía no se ha confirmado.");
        }
        User user = desafio.getUser();
        if (user == null || Boolean.TRUE.equals(user.getDeletedUser())
                || !"ACTIVE".equalsIgnoreCase(user.getStatusUser())) {
            throw new BadRequestException("El desafío no es válido.");
        }
        return user;
    }

    /**
     * Reemplaza el refresh token y devuelve un par nuevo.
       *
       * <p>Aquí hay un detalle que antes no se miraba: un token ya revocado se
       * rechazaba igual que uno inexistente, sin dejar rastro. Con la rotación
       * normal eso significa que el atacante y el titular son indistinguibles, y
       * no hay forma de saber que robaron una sesión.
       *
       * <p>Ahora se distingue. Un token revocado que vuelve a presentarse tiene
       * que ser la segunda mano: el titular ya tiene el par nuevo, así que quien
       * devuelve el viejo es alguien más. Se cierran todas las sesiones de ese
       * usuario, que es la única reacción que sirve: deja al atacante fuera y
       * obliga al titular a volver a entrar.
       *
       * <p><b>noRollbackFor</b> no es un detalle menor. Al rechazar se lanza
       * BadRequestException, y sin esto la transacción se revierte entera: la
       * detección se ejecutaba, se avisaba por log y se guardaba la auditoría
       * (esa va en REQUIRES_NEW), pero la revocación de sesiones <b>se deshacía
       * también</b>. Es decir, se detectaba el robo y no se remediaba. Con esta
       * excepción, el rechazo se confirma y lo escrito se conserva.
       */
      @Transactional(noRollbackFor = BadRequestException.class)
      public AuthResponse refresh(String rawToken) {
          String hash = hashToken(rawToken);
          Optional<RefreshToken> encontrado = refreshTokenRepository.findByTokenHash(hash);

          RefreshToken stored = encontrado
                  .filter(rt -> !rt.getRevoked())
                  .orElseGet(() -> {
                      // Si venia revocado, es reutilizacion de un token robado.
                      encontrado.ifPresent(this::detectarReutilizacion);
                      throw new BadRequestException("Refresh token inválido o revocado");
                  });

          if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
              throw new BadRequestException("Refresh token expirado");
          }

          User user = userRepository.findById(stored.getUserId())
                  .filter(u -> !u.getDeletedUser() && "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                  .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

          // Se emite el sustituto primero para poder encadenarlos.
          String nuevoToken = issueRefreshToken(user);

          stored.setRevoked(true);
          stored.setReplacedByHash(hashToken(nuevoToken));
          refreshTokenRepository.save(stored);

          return buildAuthResponse(user, buildAccessTokenFor(user),
                  nuevoToken, accessAuthorities(user));
      }

      /**
       * Cierra todas las sesiones de un usuario y deja constancia.
       *
       * <p>Se invierte el caso de uso: en la rotación normal manda el titular, y
       * aquí manda el ladrón. Cerrar todas las sesiones es lo que corta el acceso,
       * aunque el ladrón empiece a devolver tokens más nuevos de la cadena.
       */
      private void detectarReutilizacion(RefreshToken robado) {
          UUID userId = robado.getUserId();
          if (robado.getReuseDetectedAt() != null) {
              // Ya se había avisado de esta cadena. Se sigue rechazando, pero sin
              // repetir el aviso: el atacante va a insistir y no queremos ensuciar
              // el log con una línea por cada intento.
              return;
          }
          try {
              robado.setReuseDetectedAt(OffsetDateTime.now());
              refreshTokenRepository.save(robado);
              refreshTokenRepository.revokeAllByUser(userId);
              userRepository.invalidarTokensDeAcceso(userId, OffsetDateTime.now());
              systemAuditService.record("REFRESH_TOKEN_REUSE", "User", userId, null,
                      "Se reutilizó un refresh token ya rotado: se cerraron todas sus sesiones");
              log.warn("Reutilización de refresh token detectada para el usuario {}. "
                      + "Se han revocado todas sus sesiones; conviene avisarle.", userId);
          } catch (Exception e) {
              // El aviso nunca debe impedir el rechazo: si falla la escritura, el
              // token robado sigue sin servir porque la ruta de excepcion ya va a
              // tirar BadRequestException.
              log.error("No se pudo registrar la reutilización del refresh token del usuario {}",
                      userId, e);
          }
      }

    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                    // Sin esto el JWT de acceso seguía valiendo hasta que caducara,
                    // aunque la sesión ya estuviera cerrada. El corte afecta a los
                    // tokens de acceso de todos los dispositivos; los demás no lo
                    // notan, porque su refresh sigue vivo y el interceptor renueva.
                    userRepository.invalidarTokensDeAcceso(rt.getUserId(), OffsetDateTime.now());
                    systemAuditService.record("LOGOUT", "User", rt.getUserId());
                });
    }

    /**
     * Par de tokens nuevo para el usuario, tras un cambio de contraseña. El cambio
     * corta todas las sesiones, incluida la de quien lo hace; esta es la que le
     * permite seguir trabajando sin volver a iniciar sesión.
     */
    public AuthResponse sesionNueva(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        return buildAuthResponse(user, buildAccessTokenFor(user),
                issueRefreshToken(user), accessAuthorities(user));
    }

    private String buildAccessTokenFor(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getCorporateEmailUser());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        // La vida sale de la configuracion de sesion, no del yml.
        return tokenProvider.generateToken(authentication, sessionConfigService.duracion().toMillis());
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
                refreshToken,
                tokenProvider.segundosRestantes(token),
                sessionConfigService.warningMinutos(),
                sessionConfigService.autoRenew(),
                sessionConfigService.idleHabilitado(),
                sessionConfigService.idleMinutos(),
                // Los códigos de recuperación solo van en la respuesta del alta del
                // MFA, que es quien los añade después. En el login normal no hay.
                null);
    }
}
