package com.mesaayuda.controller;

import com.mesaayuda.dto.AuthRequest;
import com.mesaayuda.dto.AuthResponse;
import com.mesaayuda.dto.ChangePasswordRequest;
import com.mesaayuda.dto.ForgotPasswordRequest;
import com.mesaayuda.dto.MfaChallengeResponse;
import com.mesaayuda.dto.MfaConfirmRequest;
import com.mesaayuda.dto.ResetPasswordRequest;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.security.RefreshTokenCookie;
import com.mesaayuda.service.AuthService;
import com.mesaayuda.service.MfaService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Login, registro y cambio de contraseña")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    /** Emite y borra la cookie HttpOnly del refresh token. */
    @Autowired
    private RefreshTokenCookie refreshTokenCookie;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private MfaService mfaService;

/**
     * Login con doble factor.
     *
     * <p>Con el MFA activo hay dos salidas posibles y el cuerpo de la respuesta
     * lo dice cuál es. Si hace falta confirmar un código, no se emiten cookies:
     * los tokens solo salen tras confirmar en /auth/mfa/confirm.
     */
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve el token de acceso y emite el refresh en cookie HttpOnly. Si el doble "
                    + "factor está activo, devuelve un desafío en lugar de tokens: hay que confirmar el "
                    + "código en /auth/mfa/confirm")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request,
                                   @CookieValue(name = RefreshTokenCookie.COOKIE_EQUIPO, required = false)
                                   String equipo,
                                   HttpServletResponse response) {
        // Se autentica primero. El MFA va después: si fallara el código, la
        // contraseña ya era correcta, y el orden inverso filtraría información.
        AuthResponse res = authService.login(request);

        // Un equipo recordado (cookie) no vuelve a pedir el código durante 30 días.
        MfaChallengeResponse desafio = mfaService.prepararDesafio(
                authService.buscarPorCorreo(request.email()), equipo);
        if (desafio != null) {
            return ResponseEntity.ok(desafio);
        }

        refreshTokenCookie.emitir(response, res.refreshToken());
        return ResponseEntity.ok(res.sinRefreshToken());
    }

    /**
     * Confirmación del segundo factor.
     *
     * <p>Aquí es donde por fin se emiten tokens. Ojo al orden: se confirma el
     * código ANTES de crear la sesión, y solo si cuadra.
     *
     * <p>Si el usuario se acaba de enrolar, la misma respuesta incluye los códigos
     * de recuperación. Van en la misma respuesta y no en otra aparte a propósito:
     * así el frontend recibe siempre una sesión y nunca tiene que hacer una
     * segunda llamada para conseguir el token de acceso, que es el único dato con
     * el que puede empezar a trabajar.
     */
    @PostMapping("/mfa/confirm")
    @Operation(summary = "Confirmar el segundo factor",
            description = "Completa el alta del doble factor o verifica el inicio de sesión y devuelve "
                    + "la sesión. Es pública porque quien todavía no tiene sesión es quien la usa")
    public ResponseEntity<?> confirmarMfa(@Valid @RequestBody MfaConfirmRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse response) {
        List<String> recuperacion = mfaService.confirmar(
                request.challenge(), request.codigo(), request.codigoRecuperacion());

        AuthResponse res = authService.sesionTrasMfa(request.challenge());
        refreshTokenCookie.emitir(response, res.refreshToken());
        if (!Boolean.FALSE.equals(request.recordar())) {
            String equipo = mfaService.recordarEquipo(res.userId(), httpRequest.getHeader("User-Agent"));
            refreshTokenCookie.emitirEquipo(response, equipo,
                    java.time.Duration.ofDays(mfaService.diasRecordar()));
        }

        if (recuperacion.isEmpty()) {
            return ResponseEntity.ok(res.sinRefreshToken());
        }
        AuthResponse conCodigos = new AuthResponse(
                res.token(), res.tokenType(), res.userId(), res.email(), res.role(), res.area(),
                res.permissions(), res.mustChangePassword(), null, res.expiresInSeconds(),
                res.warningMinutes(), res.autoRenew(), res.idleEnabled(), res.idleMinutes(),
                recuperacion);
        return ResponseEntity.ok(conCodigos);
    }

    /**
     * Estado del MFA de quien está dentro, para pintar el aviso en su perfil.
     */
    @GetMapping("/mfa/estado")
    @Operation(summary = "Estado del doble factor de la cuenta actual")
    public ResponseEntity<java.util.Map<String, Object>> estadoMfa() {
        UUID userId = userService.getCurrentUser().getIdUser();
        var mfa = mfaService.estadoDe(userId);
        return ResponseEntity.ok(java.util.Map.of(
                "mfaEnabled", mfaService.activo(),
                "enrolled", mfa.map(com.mesaayuda.entity.UserMfa::isEnrolled).orElse(false),
                "codigosRestantes", mfaService.codigosRecuperacionRestantes(userId),
                "desactivable", mfa.map(com.mesaayuda.entity.UserMfa::isDesactivableUsuario).orElse(true)));
    }

/**
     * Renovar la sesión con el refresh token de la cookie.
      *
      * <p>Se exige el token CSRF en la cabecera además de la cookie. Con la cookie
      * sola, cualquier página podría hacer que el navegador renueve la sesión del
      * usuario sin que este lo notase; con el doble envío, el atacante tendría que
      * poder leer la segunda cookie, y no puede desde otro origen.
      */
     @PostMapping("/refresh")
     @Operation(summary = "Renovar la sesión",
             description = "Usa el refresh token de la cookie HttpOnly y devuelve un par nuevo. "
                     + "La petición debe traer el token CSRF en X-XSRF-TOKEN, o de otro modo "
                     + "una página de otro sitio podría renovar la sesión del usuario")
     public ResponseEntity<AuthResponse> refreshCookie(
             @CookieValue(name = RefreshTokenCookie.COOKIE_REFRESH, required = false) String refreshToken,
             HttpServletRequest request, HttpServletResponse response) {
         if (refreshToken == null || refreshToken.isBlank()) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
         }
         if (!refreshTokenCookie.csrfValido(request)) {
             systemAuditService.record("CSRF_REJECTED", "Auth", null, request.getRequestURI());
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
         }
         AuthResponse res = authService.refresh(refreshToken);
         refreshTokenCookie.emitir(response, res.refreshToken());
         return ResponseEntity.ok(res.sinRefreshToken());
     }

     @PostMapping("/logout")
     @Operation(summary = "Cerrar la sesión",
             description = "Revoca el refresh token de la cookie HttpOnly y la borra")
     public ResponseEntity<Void> logoutCookie(
             @CookieValue(name = RefreshTokenCookie.COOKIE_REFRESH, required = false) String refreshToken,
             HttpServletResponse response) {
         if (refreshToken != null && !refreshToken.isBlank()) {
             authService.logout(refreshToken);
         }
         // Se borra siempre, haya token o no: si el logout se llama cuando la
         // sesión ya estaba muerta, la cookie puede seguir ahí y hay que quitarla.
         refreshTokenCookie.borrar(response);
         return ResponseEntity.ok().build();
     }

    @PostMapping("/register")
    @Operation(summary = "Registrar usuario", description = "Registro auto-servicio (solo rol USER, dominio @coacmagisterio.fin.ec)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        AuthResponse res = authService.register(request);
        refreshTokenCookie.emitir(response, res.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(res.sinRefreshToken());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Cambiar contraseña", description = "Cambiar contraseña del usuario autenticado")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        var actual = userService.getCurrentUser();
        UUID userId = actual.getIdUser();
        // Con doble factor, cambiar la contraseña pide también el código de la app.
        mfaService.exigirCodigoSiCorresponde(actual, request.codigoMfa());
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        // Los equipos recordados se olvidan (si se cambia por sospecha de robo, el
        // otro equipo vuelve a pedir el código). Este, que acaba de dar el código,
        // queda recordado otra vez.
        mfaService.olvidarEquipos(userId);
        if (mfaService.activo() && mfaService.estadoDe(userId).map(m -> m.isEnrolled()).orElse(false)) {
            refreshTokenCookie.emitirEquipo(response,
                    mfaService.recordarEquipo(userId, httpRequest.getHeader("User-Agent")),
                    java.time.Duration.ofDays(mfaService.diasRecordar()));
        }
        // El cambio invalida todos los tokens, también los de esta sesión. Se
        // devuelve un par nuevo para que quien acaba de cambiarla siga dentro.
        AuthResponse res = authService.sesionNueva(userId);
        refreshTokenCookie.emitir(response, res.refreshToken());
        return ResponseEntity.ok(res.sinRefreshToken());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Restablecer contraseña olvidada",
            description = "Envía por correo un enlace de un solo uso. NO cambia la contraseña: eso solo ocurre "
                    + " al confirmar el enlace en /auth/reset-password. La respuesta es siempre la misma, exista o "
                    + " no la cuenta, para no permitir enumerar los correos de la organización")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message",
                "Si el correo corresponde a una cuenta activa, recibirá un enlace para establecer una nueva contraseña"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Confirmar el enlace de restablecimiento",
            description = "Fija la contraseña nueva y revoca todas las sesiones del usuario. Es público porque lo usa "
                    + " quien todavía no puede iniciar sesión")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.confirmarRestablecimiento(request.token(), request.nuevaPassword());
        return ResponseEntity.ok(Map.of("message",
                "Su contraseña fue actualizada. Ingrese con la nueva contraseña."));
    }
}