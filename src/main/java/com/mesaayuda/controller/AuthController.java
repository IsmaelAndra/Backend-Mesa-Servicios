package com.mesaayuda.controller;

import com.mesaayuda.dto.AuthRequest;
import com.mesaayuda.dto.AuthResponse;
import com.mesaayuda.dto.ChangePasswordRequest;
import com.mesaayuda.dto.ForgotPasswordRequest;
import com.mesaayuda.dto.RefreshTokenRequest;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.service.AuthService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Login, registro y cambio de contraseña")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve token JWT con rol y área del usuario")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar sesión", description = "Intercambia un refresh token válido por un nuevo par de tokens")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token activo")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar usuario", description = "Registro auto-servicio (solo rol USER, dominio @coacmagisterio.fin.ec)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Cambiar contraseña", description = "Cambiar contraseña del usuario autenticado")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = userService.getCurrentUser().getIdUser();
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Restablecer contraseña olvidada",
            description = "Restablece la contraseña por defecto y la envía por correo al email corporativo")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message", "Se restableció su contraseña temporal y fue enviada a su correo corporativo"));
    }
}